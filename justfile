set shell := ["bash", "-eu", "-c"]

# Ports for the local stack. Override inline, e.g. `WSS_APP_PORT=9090 just up`.
export WSS_APP_PORT := env_var_or_default("WSS_APP_PORT", "8080")
export WSS_DB_PORT := env_var_or_default("WSS_DB_PORT", "5432")

ssh_user := env_var_or_default("SSH_USER", env_var_or_default("USER", ""))

alias test := check
alias verify := check

check:
    pre-commit run --all-files

# Gradle's own check task (compile + tests + spotless); named distinctly so pre-commit can invoke it without recursing into `check`.
gradle-check:
    cd webapp && ./gradlew check

format:
    cd webapp && ./gradlew spotlessApply

# Install pre-commit (via uv) and the on-push git hook.
setup:
    uv tool install pre-commit
    pre-commit install --hook-type pre-push --overwrite

# Launch only the database + migrations (no webapp) on a known port. Use this when running the
# webapp itself from your IDE (IntelliJ auto-build + Spring DevTools handle live reload) against
# localhost:$WSS_DB_PORT.
db:
    docker compose up database flyway

# Fast local dev loop: Postgres + migrations in docker, webapp via `bootRun` (Spring DevTools) on
# http://localhost:$WSS_APP_PORT. A background continuous compile recompiles on every save;
# DevTools then hot-restarts the app and refreshes the browser (install a LiveReload extension for
# port 35729). Edits to Mustache templates / static assets reload without a restart. Ctrl-C stops
# the webapp, the background compile, and the docker services.
up:
    #!/usr/bin/env bash
    set -euo pipefail
    docker compose up -d --wait database
    docker compose run --rm flyway
    cd webapp
    ./gradlew -t classes &
    compile_pid=$!
    trap 'kill "$compile_pid" 2>/dev/null || true; docker compose -f ../docker-compose.yml down' EXIT
    SPRING_PROFILES_ACTIVE=local SERVER_PORT="$WSS_APP_PORT" DB_URL="localhost:$WSS_DB_PORT" ./gradlew bootRun

down:
    docker compose down -v

# Triggers prod to pull latest docker and restart services.
deploy:
    ANSIBLE_CONFIG="deploy/ansible.cfg" \
      ansible-playbook \
        -e ansible_user={{ssh_user}} \
        --inventory deploy/ansible/inventory.linode.yml \
        deploy/ansible/playbook.yml
