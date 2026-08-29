# wnc-supply-sites

Spring Boot webapp for Hurricane Helene disaster-relief supply sites. See
`README.md` for the domain, local dev setup, and ops; this file is the terse
agent-facing summary — don't duplicate the README.

## Commands

`just` is the command interface (see `justfile`):

- `just verify` — full check (`./gradlew check`); must be green before push.
- `just format` — apply spotless formatting.
- `just db` — start Postgres + Flyway migrations in docker (needed for tests).
- `just up` — run the full stack locally.

A pre-push git hook runs `verify`. Java itself is driven from `webapp/` via
`./gradlew` (e.g. `cd webapp && ./gradlew spotlessApply test`).

## Stack

Java 21 with **`--enable-preview`** (required — compile, test, and run all pass
the flag), Spring Boot 3.3, **Mustache** server-side views, **JDBI3** for data
access (**not** JPA/Hibernate — no `@Entity`, write SQL), Postgres, Flyway
migrations, Lombok, AssertJ. Frontend is static HTML/CSS/JS served by Spring.

## Migrations

Flyway files live in `schema/`, named `V<n>__description.sql`. Append-only:
**never edit an already-applied migration** — add a new one. The next number is
one past the current highest (`ls schema/`).

## Before push

`just verify` green and spotless-clean. Formatting is enforced by spotless, not
optional — run `just format` (or `./gradlew spotlessApply`) before committing.

## Tests

JUnit 5 + AssertJ under `webapp/src/test`. DAO tests run against a **real
Postgres**, so bring the database up first (`just db`); see `TestConfiguration`
and the `*Fixture` classes for shared setup.

## Worktrees

Put git worktrees in a **sibling** directory named `wnc-supply-sites-worktrees/`
next to this repo — one subdirectory per worktree — never inside the repo.
Nesting worktrees under the working copy makes recursive tooling (`./gradlew`,
tests, spotless) descend into the nested checkout; a sibling keeps them clear of
each other and needs no `.gitignore` entry.

- Create: `git worktree add ../wnc-supply-sites-worktrees/<branch> <branch>`
- Remove: `git worktree remove ../wnc-supply-sites-worktrees/<branch>`
- List:   `git worktree list`

DAO tests hit the single local Postgres from `just db`, so parallel worktrees
share one database — don't run test suites in two worktrees at once expecting
isolated data.
