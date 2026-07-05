MAKEFLAGS += --always-make --warn-undefined-variables
SHELL=/bin/bash
.SHELLFLAGS = -eu -c

check:
	cd webapp && ./gradlew check

# Launch the full stack (database + migrations + webapp) locally on known ports.
# The webapp is served at http://localhost:$(WSS_APP_PORT) and the database at localhost:$(WSS_DB_PORT).
# Override the ports inline, e.g. `WSS_APP_PORT=9090 make up`.
WSS_APP_PORT ?= 8080
WSS_DB_PORT ?= 5432
export WSS_APP_PORT
export WSS_DB_PORT

# Launch only the database + migrations (no webapp) on a known port. Use this when running the
# webapp itself from your IDE against localhost:$(WSS_DB_PORT).
db:
	docker compose up database flyway

up:
	cd webapp && ./gradlew bootJar
	docker compose up

up-detached:
	cd webapp && ./gradlew bootJar
	docker compose up -d

down:
	docker compose down -v
