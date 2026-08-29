set shell := ["bash", "-eu", "-c"]

# Ports for the local stack. Override inline, e.g. `WSS_APP_PORT=9090 just up`.
export WSS_APP_PORT := env_var_or_default("WSS_APP_PORT", "8080")
export WSS_DB_PORT := env_var_or_default("WSS_DB_PORT", "5432")

ssh_user := env_var_or_default("SSH_USER", env_var_or_default("USER", ""))

check:
    pre-commit run --all-files

verify:
    cd webapp && ./gradlew check

format:
    cd webapp && ./gradlew spotlessApply

# Install pre-commit (via uv) and the on-push git hook.
setup:
    uv tool install pre-commit
    pre-commit install --hook-type pre-push --overwrite

# Launch only the database + migrations (no webapp) on a known port. Use this when running the
# webapp itself from your IDE against localhost:$WSS_DB_PORT.
db:
    docker compose up database flyway

_bootjar:
    cd webapp && ./gradlew bootJar

# Launch the full stack (database + migrations + webapp) locally on known ports. The webapp is
# served at http://localhost:$WSS_APP_PORT and the database at localhost:$WSS_DB_PORT.
up: _bootjar
    docker compose up

up-detached: _bootjar
    docker compose up -d

down:
    docker compose down -v

# Triggers prod to pull latest docker and restart services.
deploy:
    ANSIBLE_CONFIG="deploy/ansible.cfg" \
      ansible-playbook \
        -e ansible_user={{ssh_user}} \
        --inventory deploy/ansible/inventory.linode.yml \
        deploy/ansible/playbook.yml
