MAKEFLAGS += --always-make --warn-undefined-variables
SHELL=/bin/bash
.SHELLFLAGS = -eu -c

check:
	cd webapp && ./gradlew check
