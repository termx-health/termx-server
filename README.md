# TermX Server

A FHIR-compliant terminology server for managing and serving healthcare terminologies.

## Versioning

The application version is defined in the `VERSION` file in the project root; it is read by `build.gradle.kts` at build time and overrides `gradle.properties`.

## Features

### 🌐 FHIR Terminology Ecosystem Integration (New!)

Discover and resolve terminology servers across distributed healthcare systems.

- **Interactive Web UI**: Visual interface at `/tx-ecosystem/` for exploring servers
- **REST API**: Standards-compliant endpoints at `/tx-reg` for programmatic access
- **Discovery**: Find all registered terminology servers with flexible filtering
- **Resolution**: Identify authoritative servers for specific CodeSystems/ValueSets
- **Download**: Export server information as JSON files
- **Public Access**: No authentication required

**Quick Start:**
- Web UI: https://dev.termx.org/tx-ecosystem/
- API: https://dev.termx.org/tx-reg

**Documentation:**
- [Feature Description](docs/features/fhir-terminology-ecosystem-feature-description.md) - Overview and use cases
- [Technical Guide](docs/features/fhir-terminology-ecosystem-api.md) - API reference and configuration
- [UI Guide](docs/features/fhir-terminology-ecosystem-ui.md) - Web interface documentation

### Other Features

- **CodeSystem & ValueSet Management**: Create, version, and manage code systems and value sets
- **FHIR Operations**: Support for $expand, $validate-code, $lookup, $translate, and more
- **SNOMED CT Integration**: Integration with Snowstorm for SNOMED CT support
- **Multi-language Support**: Designations and translations in multiple languages
- **Excel Import/Export**: Import and export terminologies via Excel/CSV files

For complete feature documentation, see the [docs/features](docs/features) directory.

## Init postgres docker 
Or you can also use an existing database.

Pull postgres public image.
```bash 
docker pull postgres:14
```  
Run Docker container
```bash 
docker run -d --restart=unless-stopped --name termx-postgres -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:14
``` 
Connect and create database\users using the following command
```bash 
docker exec -i termx-postgres psql -U postgres <<-EOSQL
CREATE ROLE termserver_admin LOGIN PASSWORD 'test' NOSUPERUSER INHERIT NOCREATEDB CREATEROLE NOREPLICATION;
CREATE ROLE termserver_app   LOGIN PASSWORD 'test' NOSUPERUSER INHERIT NOCREATEDB CREATEROLE NOREPLICATION;
CREATE ROLE termserver_viewer NOLOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE DATABASE termserver WITH OWNER = termserver_admin ENCODING = 'UTF8' TABLESPACE = pg_default CONNECTION LIMIT = -1;
grant temp on database termserver to termserver_app;
grant connect on database termserver to termserver_app;
CREATE EXTENSION IF NOT EXISTS hstore schema public;
EOSQL
```

If you need to create a separate db for testing:
```bash 
docker exec -i termx-postgres psql -U postgres <<-EOSQL
CREATE DATABASE termserver_new WITH OWNER = termserver_admin ENCODING = 'UTF8' TABLESPACE = pg_default CONNECTION LIMIT = -1;
grant temp on database termserver_new to termserver_app;
grant connect on database termserver_new to termserver_app;
EOSQL
```
Or recreate it with the helper script:
```bash
./recreate-termserver-db.sh
```
To target a different database name:
```bash
./recreate-termserver-db.sh termserver_new
```
In case you are using an existing database, run SQL commands between EOSQL via sql console.

## Navigate to app folder and run application in the development mode 
```bash 
cd termx-app
./gradlew run -Pdev
``` 
In the development mode you can use application without authentication. The application use special dev token **Bearer token** `yupi` in request Authorization header.

### Yupi privilege override (QA / migration testing)

By default the `yupi` session is granted the full set of action wildcards (`*.*.view`, `*.*.triage`, `*.*.edit`, `*.*.publish`). To test the application as a user with a restricted privilege set -- for example, to verify that a `View`-only user no longer sees CodeSystem download links or wiki comments after the Phase A migration -- override the yupi privilege set with the `-PyupiPrivileges` Gradle flag (or directly with `-Dauth.dev.yupi.privileges=...`).

```bash
# View-only: page renders but downloads + comments are hidden.
./gradlew :termx-app:run -Pdev -PyupiPrivileges='*.*.view'

# View + Triage: downloads visible, comment thread visible, no edit.
./gradlew :termx-app:run -Pdev -PyupiPrivileges='*.*.view,*.*.triage'

# Resource-scoped view: only the icd-10 CodeSystem is viewable.
./gradlew :termx-app:run -Pdev -PyupiPrivileges='icd-10.CodeSystem.view'
```

The value is a comma-separated list of dotted privilege strings (`{resource}.{type}.{action}`); whitespace and empty tokens are ignored. Common preset values and their expected UI effect are documented in [docs/specification/terminology-server/privileges-migration-guide.md](../docs/specification/terminology-server/privileges-migration-guide.md#qa-testing-with-the-yupi-privilege-override) and inline in [`YupiSessionProvider`](termx-app/src/main/java/org/termx/auth/YupiSessionProvider.java).

For ad-hoc per-request overrides without restarting the server, send a JSON-encoded `SessionInfo` directly in the header:

```
Authorization: Bearer yupi{"username":"qa","privileges":["*.CodeSystem.view"]}
```

**Frontend note.** The web UI (termx-web) fetches privileges from the backend's `/auth/userinfo` endpoint, so any `auth.dev.yupi.privileges` override is reflected in the UI automatically. After changing the override and restarting the server, **hard-refresh the browser** (Cmd/Ctrl-Shift-R) to drop the cached session. If you still see admin (`*.*.*`) after the refresh, check that:
1. The frontend's `environment.yupiEnabled` is `true` (default in `environment.ts`).
2. The backend started with `-Pdev` so `auth.dev.allowed=true` is set.
3. The `-PyupiPrivileges` value made it onto the JVM command line (visible in `./gradlew :termx-app:run` output via `-Dauth.dev.yupi.privileges=...`).
4. Hit `http://localhost:8200/auth/userinfo` directly in the browser. The response should show `"username": "yupi"` with the privileges you configured. If it shows a different username (e.g. `admin`) you are hitting the **mock** auth provider configured in `application-local.yml` -- the yupi provider is wired to take priority over mock since order=3 (yupi) < 5 (mock), so this should not happen, but if your `application-local.yml` is heavily customised, double-check that nothing else is intercepting `Bearer yupi`.

### Logging

- **Local (verbose)**: Copy [`termx-app/src/main/resources/application-local.example.yml`](termx-app/src/main/resources/application-local.example.yml) to `termx-app/src/main/resources/application-local.yml` (that file is gitignored). Micronaut only loads `application-local.yml` when the **`local`** environment is active. Use **`./gradlew :termx-app:run -Pdev`** or **`./_run_local.sh`** (they set `dev,local`), or add **`-Dmicronaut.environments=dev,local`** to the JVM when running from the IDE. **`./gradlew run` without `-Pdev` does not load `application-local.yml`**, so `logger.levels` there will have no effect.
- **Docker**: Default root level is **INFO** unless overridden. Set **`LOGBACK_LOG_LEVEL`** (e.g. `DEBUG` for troubleshooting) or pass **`-DLOGBACK_LOG_LEVEL=...`** via **`JAVA_OPTS`**. For Docker Compose, add a line in [`deployment/docker-compose/server.env`](deployment/docker-compose/server.env).

### Running automated tests

From the repository root, use Gradle:

```bash
# All modules
./gradlew test

# One module (examples)
./gradlew :terminology:test
./gradlew :termx-app:test

# One test class
./gradlew :terminology:test --tests "org.termx.terminology.fileimporter.codesystem.CodeSystemFileImportProcessorTest"
```

HTML reports are under each module’s `build/reports/tests/test/` (e.g. `terminology/build/reports/tests/test/index.html`).

### Test
Query `http://localhost:8200/fhir/metadata` in the browser.
It should return CapabilityStatement resource.


## Authentication

### Keyclock
The terminology server requires authenticated users. Any authentication server supporting Open-Id connect should suffice. For our development, 
we are using [Keycloak](https://www.keycloak.org/). Check official [docs](https://www.keycloak.org/guides#getting-started) for setup. 
Check the [example](https://wiki.kodality.dev/terminology-server/guide/authentication#keycloak) of the configuration.

### Run application with authentication
```bash 
./gradlew run
```

## Snowstorm 
Snowstorm server serves SNOMED terminology and may be installed if you need SNOMED. 
Check Snowstorm installation and configuration [documentation](https://wiki.kodality.dev/terminology-server/snowstorm).

After installation add properties `snowstorm.url`, `snowstorm.user`, `snowstorm.password`, `snowstorm.namespace` to `application.yml` file.


## MinIO

```shell
docker run \
  -p 9000:9000 \
  -p 9001:9001 \
  --name termx-minio \
  -e "MINIO_ROOT_USER=minio" \
  -e "MINIO_ROOT_PASSWORD=supersecretpass" \
  -d \
  quay.io/minio/minio server /data --console-address ":9001"
```
