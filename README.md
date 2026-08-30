# WNC Supply Sites

Live URL: [https://WNC-Supply-Sites.com]

Webapp for Hurricane Helene disaster relief. The website provides
a search and simple inventory management interface for supply sites.
This allows supply sites to indicate their needs, urgent & surplus
items. Ultimately the goal is to allow for supply items to be redistributed
from sites that have too much, to those that need those items.

## Activating beta features

Some in-development features are hidden behind opt-in browser cookies, so they
can ship to production while only being visible to people who opt in. To enable
them, paste the matching one-line command into your browser's address bar and
press enter — it sets the cookie and reloads the page.

| Feature | Command |
| --- | --- |
| Volunteer Portal button (homepage) | `javascript:document.cookie="beta-volunteer=true;path=/";location.reload()` |

To turn a feature back off, clear the cookie in your browser's dev tools, or
paste the same command with an expired date, e.g.
`javascript:document.cookie="beta-volunteer=;path=/;expires=Thu, 01 Jan 1970 00:00:00 GMT";location.reload()`.

## Ops

See the [ops-docs](docs/ops.md)

## Development

### Quick Start - Backend Dev
- clone the code
- install intellij
- install Java 21 (`brew install --cask temurin@21` on Mac)
- install docker
- install [just](https://just.systems) (`brew install just` on Mac)
- right click the webapp/build.gradle file & select 'link project'
- start the database (postgres + migrations, in docker): `just db`
- Run `SuppliesDatabaseApplication.java`
  - in run configuration, set the environment variable: `WEBHOOK_SECRET=secret`
  - in run configuration, set the environment variable: `DEFAULT_DEPLOYMENT_ENABLED=true`

To do full tests and formatting before commit & push:
- `cd webapp; ./gradlew spotlessApply test`

### Quick Start - Frontend Dev
- clone the code
- open any of the .html file in a web browser
- update the corresponding HTML, CSS & JS files; check-in & push

#### R-Commons Volunteer Portal (`/rcommons/`)
R-Commons is a volunteer-facing portal integrated into the app as static files served by Spring Boot.
- Access at: `http://localhost:8080/rcommons/onboarding.html`
- Linked from the homepage via the "Volunteer Portal" button
- Source files: `webapp/src/main/resources/public/rcommons/`
- Backend endpoint: `GET /rcommons/api/sites` (see `RCommonsController.java`)

### Quick Start - dockerized

- install docker
- install a Java 21 JDK (the webapp runs on the host via Gradle; docker only runs Postgres)
- install [just](https://just.systems) (`brew install just` on Mac)
- clone the code
- cd to the project directory
- run: `just up` (launches Postgres + migrations in docker, then runs the webapp with live reload)
- access the webapp at http://localhost:8080
- stop and clean up with `just down` (or Ctrl-C, which also tears down the docker services)
- ports are overridable, e.g. `WSS_APP_PORT=9090 WSS_DB_PORT=5433 just up`

`just up` runs the webapp via `bootRun` with Spring DevTools: it recompiles on
save (a background `gradle -t classes` watch), hot-restarts the app, and, with a
LiveReload browser extension, auto-refreshes the page. Mustache template and
static-asset edits reload without a restart.

It runs the webapp under the `local` Spring profile, which seeds a known admin
login so you can sign in without the SMS password-setup flow:

- phone: `11111111111`
- password: `wncstrong`
- role: `DATA_ADMIN` (god mode)

This login only exists under the `local` profile and is never present in deployed
environments.

### Pre-commit

Run `just setup` to configure pre-commit to run on push.

### Branching Strategy & Workflow

- Ship, Show, Ask: https://martinfowler.com/articles/ship-show-ask.html

- Keep a linear history, do not push merge commits to master!
  - use merge with fast forward only or rebase


### Local Setup

The database runs in docker via `docker-compose.yml` — no bare-metal postgres needed.

- `just db` — start postgres + apply migrations (use when running the webapp from your IDE)
- `just up` — start postgres + migrations in docker, then run the webapp on the host with live reload
- `just down` — stop everything and remove the database volume (wipes local data)

Database, user, and schema creation are handled automatically:
- `./.docker-compose/database/01-init.sql` creates the `wnc_helene` database and user on first
  startup.
- the `flyway` compose service applies all `schema/V*.sql` migrations to `wnc_helene`
  (the database the app uses locally; the Gradle `test` task brings up its own throwaway
  copy on an ephemeral port).

To recreate the database from scratch, run `just down` (removes the volume) then `just db`.

The default ports are `5432` (database) and `8080` (webapp); override with `WSS_DB_PORT` /
`WSS_APP_PORT`.

#### Docker

- if on Mac, be sure to go to settings, file & folder permissions, and allow 'Docker' to access 'Documents' folder
- if on Mac, be sure to configure docker to be installed as a system resource
- TODO: docker install steps

Access local DB (on docker)
```
docker exec -it wnc-supply-sites-database-1 bash
su postgres
psql
\c wnc_helene
```

#### Access local DB (on bare-metal)
```bash
sudo -u postgres psql
\c wnc_helene
```
- The databases will have empty data. Example data can be found in `src/test/resources/TestData.sql`

- Finally, the app can be launched via Intellj IDE, main class is `SuppliesDatabaseApplication.java`
  - The app can also be likely be run via gradle `./gradlew bootRun` (not well tested/vetted, but should work)

- A few environment variables need to be set. This can be done in the run config in IntelliJ:
  - WEBHOOK_SECRET  (can be set to any value)

### Development - Running unit tests

- Tests run primarily through IntelliJ IDE, right click 'test' folder & run
- Test can be run with gradle as well `./gradlew test`


### Tech Stack

- springboot
- JDBI
- postgres
- mustache
- vanilla JS
- aspectJ (for testing)
- gradle

#### R-Commons (`/rcommons/`)
- vanilla JS (SPA with client-side routing)
- CSS custom properties (design tokens)
- Instrument Sans (Google Fonts)

### Tech Stack Non-Choices

Do not bring these frameworks in, these frameworks are intentionally rejected:
- spring security (if we do integration with a system that uses OAuth2 tokens, like
  FB, or google, then perhaps yes)
- JPA
- guava (just avoid, favor to copy/paste their implementations into a Util class)


### Project Layout

- `webapp/`
  - This is the interesting part that is the webapp.
- `schema/`
  - contains DB migration files

#### HTML Page Layout

A page's CSS and JS must resolve from its server URL, which isn't tied to where
the template or asset lives on disk — a controller can serve "/page/details/data"
from a template at "/page/data.html". Relative asset links like "../details.css"
resolve off the request URL, so the same link silently breaks at a different URL
depth. Keep assets at a fixed location and reference them absolutely instead.

**Watch out for controller-shadowed URLs.** A static file only serves if no
controller claims its URL. Some controllers own an entire URL prefix via a path
variable — e.g. `DeliveryController` maps `/delivery/{publicUrlKey}`, so *any*
request under `/delivery/...` (including `/delivery/foo.css`) is routed to the
controller instead of the static file, even when that file physically sits in
`public/delivery/`. Putting a page's CSS/JS in such a directory makes it
unreachable — the request 400s/404s and the page renders unstyled.

So keep per-page CSS/JS at the public root (`public/`, served from `/`), which no
controller shadows. That's why `delivery.css`, `deliveries-board.css`, and
`deliveries-board.js` live at the root rather than beside their `.html` templates
under `public/delivery/`. Reference them with an absolute path
(`/deliveries-board.css`) so they resolve the same no matter how deep the page's
own URL is.

### Env Variables

Configuration values are in 'application.properties'.
The config values all have defaults and will work out of the box.
To override config values, set the appropriate environment variables in IntelliJ,
launch configuration. It will look something like this:
<img width="530" alt="env variables config in IntelliJ"
src="https://github.com/user-attachments/assets/5237ac05-a0f9-4fc0-aaa2-98944364c821">


### Code Organization - DAO's and Controllers

Controller classes are classic spring webserver endpoints. Controller
methods should handle control flow and ideally get actions done
by calling functional private static methods or DAO methods.

Controller's and packages are organized by functionality.

If a controller is 'tight', pretty small and straight forward,
then the DB access methods might sometimes be directly in the controller.
Otherwise usually DB access code will be in an adjacent "DAO" class.

### Code Formatting

Use google-java-format Intellij plugin.

Formatting can be applied with gradle 'spotless' plugin: `./gradlew spotlessApply`


### Writing unit tests

- all DB queries should be tested in isolation.
- controller logic is ideally tested end-to-end, invoke the controller
  endpoint with a JSON or Map payload and then validate you
  get the right response and/or that the DB changes appropriately
- it's okay to write simple DB queries in the test code to validate
  the system behavior.

### System Authentication

A user is logged in if they have an auth cookie that contains
the correct secret value. There is a RequestInterceptor that
checks a requested URLs prefix and then checks for that cookie
if the URL requires authentication.

The magic cookie value is set by environment variable on startup.
If the cookie value matches, it is valid, otherwise there is
a redirect to the login page.

-----

In the future, it is intended to have individual logins. In which
case the correct cookie value will be a value stored in database.
After a user logs in, we would store a token value in DB,
then we'd check the DB for this token value.

## Deployment

- git push to master
- docker image is automatically built
- ssh to server
- run any psql migrations:
  - `sudo -u postgres psql; \c wnc_helene;`
- run `/root/redeploy.sh`
