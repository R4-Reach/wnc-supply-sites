# wnc-supply-sites

Spring Boot webapp for Hurricane Helene disaster-relief supply sites. See
`README.md` for the domain, local dev setup, and ops; this file is the terse
agent-facing summary — don't duplicate the README.

## Commands

`just` is the command interface (see `justfile`):

- `just check` — full validation via pre-commit (which includes `gradle-check`); must be green before push. Aliased as `just test` and `just verify`.
- `just gradle-check` — Gradle's `./gradlew check` on its own (compile + tests + spotless).
- `just format` — apply spotless formatting.
- `just db` — start Postgres + Flyway migrations in docker (needed for tests).
- `just up` — Postgres + migrations in docker, webapp via `bootRun` with live reload.

A pre-push git hook runs `check` (pre-commit). Java itself is driven from `webapp/` via
`./gradlew` (e.g. `cd webapp && ./gradlew spotlessApply test`).

## Stack

Java 21 with **`--enable-preview`** (required — compile, test, and run all pass
the flag), Spring Boot 3.3, **Mustache** server-side views, **JDBI3** for data
access (**not** JPA/Hibernate — no `@Entity`, write SQL), Postgres, Flyway
migrations, Lombok, AssertJ. Frontend is static HTML/CSS/JS served by Spring.

## `/rcommons` is a foreign transplant — don't treat it as the house style

The volunteer portal under `webapp/src/main/resources/public/rcommons/` (and its
`RCommonsController`) was built as a **separate app** and dropped in wholesale. It
does not follow this repo's conventions and should not be used as a pattern to
copy: it's a vanilla-JS SPA with client-side routing, its own CSS design-token
system (`rcommons/css/tokens.css`), and a JS module split, whereas the rest of the
app is server-rendered Mustache with one script/style per page. Its API layer
(`rcommons/js/api.js`) is only partly wired — several calls still return
`localStorage`/`DEMO_*` stubs rather than hitting the backend.

It's gated behind the `beta-volunteer` cookie (see `README.md`), so it ships to
prod without being visible. The intended direction is to **migrate it to match
the rest of the app**, not to spread its patterns outward. When touching it,
treat it as legacy-to-be-converted; when building elsewhere, ignore it as a
reference.

## Migrations

Flyway files live in `schema/`, named `V<n>__description.sql`. Append-only:
**never edit an already-applied migration** — add a new one. The next number is
one past the current highest (`ls schema/`).

## Before push

`just check` green and spotless-clean. Formatting is enforced by spotless, not
optional — run `just format` (or `./gradlew spotlessApply`) before committing.

## Tests

JUnit 5 + AssertJ under `webapp/src/test`. DAO tests run against a **real
Postgres**. The Gradle `test` task provisions its own throwaway database via
docker-compose — ephemeral port, migrations applied, torn down afterwards — so
`just test`/`check` need nothing running first. Only **IDE** test runs need a
database up beforehand (`just db`); they fall back to `localhost:5432`. See
`TestConfiguration` and the `*Fixture` classes for shared setup.

## Worktrees

Put git worktrees in a **sibling** directory named `wnc-supply-sites-worktrees/`
next to this repo — one subdirectory per worktree — never inside the repo.
Nesting worktrees under the working copy makes recursive tooling (`./gradlew`,
tests, spotless) descend into the nested checkout; a sibling keeps them clear of
each other and needs no `.gitignore` entry.

- Create: `git worktree add ../wnc-supply-sites-worktrees/<branch> <branch>`
- Remove: `git worktree remove ../wnc-supply-sites-worktrees/<branch>`
- List:   `git worktree list`

Each checkout's Gradle `test` task uses a docker-compose project name derived
from the worktree directory and `$USER`, on an ephemeral port — so test suites in
sibling worktrees (and a running `just up`/`just db`) stay isolated and can run
in parallel without sharing a database. (IDE test runs are the exception: they
fall back to the single `localhost:5432` from `just db` and do share it.)
