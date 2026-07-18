# TermX Deployment & Configuration Reference (DevOps)

A single place listing the configurable variables for a TermX deployment, organised by repository and
logical group. Use it to fill in `server.env`, `web.env`, `pg.env` and the companion-service env files.

Sources: `termx-server` (`termx-app/src/main/resources/application*.yml`, `termx-app/Dockerfile`),
`termx-web` (`app/src/environments/`, container entrypoint), the [`termx-quick-start`](https://github.com/termx-health/termx-quick-start)
compose stack, the public installation guide, and the feature docs in this folder.

---

## How configuration is applied

| Component | Mechanism | Where you set it |
|---|---|---|
| **termx-server** (Micronaut) | Every `application.yml` property is overridable by an env var via Micronaut **relaxed binding**: `UPPER_SNAKE_CASE` → dotted property. e.g. `SNOWSTORM_URL` → `snowstorm.url`, `BOB_MINIO_ACCESS_KEY` → `bob.minio.access-key`. | `server.env` (compose `env_file`) |
| **termx-web** (Angular) | Runtime config, **not** build-time. The container runs `envsubst` over `assets/env.js` → `window.twConfig`, read by `environment.ts`. JSON-typed values must be valid JSON. | `web.env` |
| **PostgreSQL** | Standard `postgres` image env. | `pg.env` |

> **Gotcha — colons in URL defaults.** In `application.yml`, URL defaults are wrapped in backticks
> (`` ${BOB_MINIO_URL:`http://localhost:9000`} ``) because Micronaut treats `:` as the value/default
> delimiter. When you *set* the env var you do **not** add backticks — just the plain URL.

> **Selecting a profile.** `MICRONAUT_ENVIRONMENTS=<name>` loads `application-<name>.yml`
> (e.g. `demo`, `dev`). Profile files ship example values; env vars still override them.

---

## 1. termx-server — `server.env`

### 1.1 Database (required)
| Env var | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/termx` | JDBC URL of the TermX database |
| `DB_APP_PASSWORD` | `test` | Password for the `tx_app` runtime role |
| `DB_ADMIN_PASSWORD` | `test` | Password for the `tx_admin` role (owns schema, runs Liquibase) |
| `DB_POOL_SIZE` | `10` | HikariCP connection-pool size |

### 1.2 Authentication — token validation (required in production)
| Env var | Default | Purpose |
|---|---|---|
| `OAUTH_JWKS_URL` | `fixme` | OIDC JWKS endpoint used to validate incoming JWTs. **Set this** (or enable mock auth). |
| `OAUTH_JWKS_CACHE_TTL_SECONDS` | `3600` | JWKS cache TTL |
| `GUEST_DISABLED` | `false` | `true` blocks unauthenticated/guest access |

Development-only auth (do **not** use in production):
| Env var | Default | Purpose |
|---|---|---|
| `AUTH_MOCK_ENABLED` | `false` | Enable the mock auth provider (bypasses OIDC) |
| `AUTH_MOCK_DEFAULT_USER` | `admin` | User assumed when no `Authorization` header is sent |
| `AUTH_MOCK_USERS_FILE` | `mock/users.json` | Classpath JSON of mock users/privileges |

### 1.3 User directory — Keycloak Admin API (optional; needed for the user-management UI)
| Env var | Property | Purpose |
|---|---|---|
| `KEYCLOAK_URL` | `keycloak.url` | Keycloak **admin** realm API (`…/admin/realms/<realm>`) |
| `KEYCLOAK_SSO_URL` | `keycloak.sso-url` | Token/OIDC endpoint (`…/realms/<realm>/protocol/openid-connect`) |
| `KEYCLOAK_CLIENT_ID` | `keycloak.client-id` | Service client id used to query users |
| `KEYCLOAK_CLIENT_SECRET` | `keycloak.client-secret` | Service client secret |

### 1.4 SNOMED CT / Snowstorm (optional; required for SNOMED features)
| Env var | Default | Purpose |
|---|---|---|
| `SNOWSTORM_URL` | `https://snowstorm.termx.org/` | Snowstorm base URL |
| `SNOWSTORM_USER` | `termserver-app` | Basic-auth user (leave empty for read-only public servers) |
| `SNOWSTORM_PASSWORD` | `` | Basic-auth password |
| `SNOWSTORM_BRANCH` | `MAIN` | Default edition branch, e.g. `MAIN/SNOMEDCT-EE` |
| `SNOWSTORM_NAMESPACE` | `1000265` | Namespace id for minting SCTIDs (your NRC namespace) |

### 1.5 Object storage — MinIO / S3 (Bob) (optional; required for SNOMED/LOINC import & wiki files)
| Env var | Default | Purpose |
|---|---|---|
| `BOB_MINIO_URL` | `http://localhost:9000` | S3/MinIO endpoint (`bob.minio.url`) |
| `BOB_MINIO_ACCESS_KEY` | `minio` | Access key (`bob.minio.access-key`) |
| `BOB_MINIO_SECRET_KEY` | `minio123` | Secret key (`bob.minio.secret-key`) |

> The `application.yml` placeholders are `BOB_MINIO_URL` / `BOB_MINIO_ACCESS_KEY` / `BOB_MINIO_SECRET_KEY`.
> The `BOB_` prefix keeps the app's access credentials distinct from the MinIO server's own root
> account (`MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`). The older unprefixed `MINIO_URL` /
> `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` names are **no longer read** — use `BOB_MINIO_*`.

### 1.6 Large terminology import (SNOMED + LOINC archives) — see [`snomed-import-management.md`](snomed-import-management.md), [`loinc-import-management.md`](loinc-import-management.md)
| Env var / property | Default | Purpose |
|---|---|---|
| `micronaut.server.max-request-size` | `629145600` (600 MB) | Max multipart upload (SNOMED International RF2 ≈ 549 MB) |
| `micronaut.server.multipart.max-file-size` | `629145600` (600 MB) | Per-file multipart cap (same value) |

> **Delta-generator jar is bundled — no env var.** The IHTSDO `DeltaGeneratorTool-3.0.0.jar` is
> vendored in the image (`snomed/src/main/resources/snomed/delta-generator-tool/`) and auto-extracted
> to the JVM temp dir at startup. There is no `SNOMED_DELTA_GENERATOR_JAR` setting (the earlier
> `/opt/delta-generator-tool.jar` variable was removed).

### 1.7 Mail / SMTP (optional) — see [`smtp-email-support.md`](smtp-email-support.md)
| Env var | Default | Purpose |
|---|---|---|
| `SMTP_ENABLED` | `false` | Master switch for outbound email |
| `SMTP_HOST` | `` | SMTP server host |
| `SMTP_PORT` | `587` | SMTP port |
| `SMTP_USERNAME` | `no-auth-required` | SMTP user |
| `SMTP_PASSWORD` | `no-auth-required` | SMTP password |
| `SMTP_FROM` | `noreply@termx.dev` | From address |
| `SMTP_AUTH` | `true` | Use SMTP AUTH |
| `SMTP_STARTTLS` | `true` | Use STARTTLS |
| `SMTP_TO_IMPORT` | `` | Recipients for import notifications |

### 1.8 FHIR terminology ecosystem & search defaults (optional)
| Env var | Default | Purpose |
|---|---|---|
| `TERMINOLOGY_ECOSYSTEM_URL` | `http://tx.fhir.org/tx-reg` | tx-registry coordination server |
| `TERMX_FHIR_CS_SEARCH_DEFAULT_SUMMARY` | `true` | Default `_summary` for `GET /fhir/CodeSystem` |
| `TERMX_FHIR_VS_SEARCH_DEFAULT_SUMMARY` | `true` | Default `_summary` for `GET /fhir/ValueSet` |
| `TERMX_FHIR_CM_SEARCH_DEFAULT_SUMMARY` | `true` | Default `_summary` for `GET /fhir/ConceptMap` |

### 1.9 GitHub integration (optional; for IG authoring/publishing)
| Env var | Default | Purpose |
|---|---|---|
| `GITHUB_APP_NAME` | (none) | GitHub App name |
| `GITHUB_APP_ID` | (none) | GitHub App id |
| `GITHUB_CLIENT_ID` | (none) | GitHub App client id |
| `GITHUB_CLIENT_SECRET` | (none) | GitHub App client secret |

### 1.10 Public URLs / CORS
| Env var | Default | Purpose |
|---|---|---|
| `TERMX_WEB_URL` | `https://demo.termx.org` | Public web URL (`termx.web-url`; used by static-site generation) |
| `TERMX_API_URL` | `https://demo.termx.org/api` | Public API URL (`termx.api-url`) |
| `MICRONAUT_SERVER_CORS_ENABLED` | `false` | Enable CORS (dev/cross-origin setups) |
| `MICRONAUT_SERVER_CORS_CONFIGURATIONS_UI_ALLOWED_ORIGINS` | (none) | Allowed origin(s) for the UI |

### 1.11 Build / version (surfaced on `GET /api/info`)
| Env var | Default | Purpose |
|---|---|---|
| `APP_VERSION` | `dev` | Version string (image tag) |
| `BUILD_TIME` | `` | Build timestamp (set as Docker build-arg) |
| `GIT_COMMIT` | `` | Short commit sha (build-arg) |
| `PR_NUMBER` | `` | Merged PR number (build-arg) |

### 1.12 JVM / runtime / logging
| Env var | Default | Purpose |
|---|---|---|
| `JAVA_OPTS` | (none) | JVM flags, e.g. `-Xmx1800m` |
| `JVM_OOM_OPTS` | `-XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/` | OOM fail-fast (set in Dockerfile) |
| `LOGBACK_LOG_LEVEL` | `INFO` | Root log level |
| `LIQUIBASE_LOG_LEVEL` / `MICRONAUT_LIQUIBASE_LOG_LEVEL` | `INFO` | Liquibase log level |

### 1.13 Azure DevOps git sync (optional; 3.3+)

PAT-based Azure DevOps integration mirroring the GitHub content workflow. The bean is **opt-in** and
requires `ms-devops.client.id` — when unset, the MS DevOps endpoints return "not configured" and the
rest of the app is unaffected.

| Env var | Property | Default | Purpose |
|---|---|---|---|
| `MS_DEVOPS_CLIENT_ID` | `ms-devops.client.id` | (none) | Arbitrary username paired with the PAT; presence of this key enables the feature |
| `MS_DEVOPS_CLIENT_SECRET` | `ms-devops.client.secret` | (none) | Azure DevOps **Personal Access Token** |

### 1.14 Terminology-server secret encryption (optional; 3.3+)

Encrypts stored external-terminology-server secrets (OAuth2 client secrets, API keys, basic-auth
passwords) at rest with AES/GCM. **Opt-in**: when unset, secrets are stored as plaintext
(passthrough), so it can be adopted later without a data migration.

| Env var | Property | Default | Purpose |
|---|---|---|---|
| `TERMX_SERVER_SECRET_ENCRYPTION_KEY` | `termx.server.secret-encryption-key` | (none) | Base64-encoded AES key; enables encryption of terminology-server secrets |

> The per-server auth type (`none` / `basic` / `oauth2` / `apikey`) and OAuth2 `scope` are stored
> **per terminology server** (DB config via the Servers UI), not as env vars.

### 1.15 FHIR terminology conformance testing (optional; 3.3+) — see [`../fhir-tx-conformance-todo.md`](../fhir-tx-conformance-todo.md)

Runs the official HL7 `tx-ecosystem` suite against this server via the FHIR validator's `txTests`.
In the shipped image `validator_cli.jar` is downloaded in the Dockerfile and
`TERMX_CONFORMANCE_VALIDATOR_JAR` is pre-set.

| Env var | Property | Default | Purpose |
|---|---|---|---|
| `TERMX_CONFORMANCE_VALIDATOR_JAR` | `termx.conformance.validator-jar` | (baked into image) | Absolute path to `validator_cli.jar` |
| `TERMX_CONFORMANCE_TX_URL` | `termx.conformance.tx-url` | `http://localhost:8200/fhir` | This server's externally-reachable FHIR base, used as the `-tx` target |
| `TERMX_CONFORMANCE_TEST_PACKAGE_DIR` | `termx.conformance.test-package-dir` | (validator package cache) | Dir holding the tx-ecosystem test fixtures for `loadSetup=true` runs |
| `TERMX_CONFORMANCE_SETUP_AUTH_TOKEN` | `termx.conformance.setup-auth-token` | (none) | Bearer token the setup loader uses to write fixtures (needs CS/VS create+update privileges) |

### 1.16 Global search (3.3+)

The global-search dashboard's quick-filter spaces are driven by a per-space **`global_search`** flag
(column on `sys.space`, toggled in the Space editor), not by configuration. No env var; listed here so
operators know the dashboard is data-driven rather than env-driven.

---

## 2. termx-web — `web.env`

### 2.1 Backend connection (required)
| Env var | Default | Purpose |
|---|---|---|
| `TERMX_API` | `/api` | Base URL of the TermX server API |
| `BASE_HREF` | `/` | App base path (use `/termx/` when served on a sub-path) |

### 2.2 Authentication — OIDC (browser) (required in production)
| Env var | Default | Purpose |
|---|---|---|
| `OAUTH_ISSUER` | (required) | OIDC issuer (e.g. Keycloak realm URL) |
| `OAUTH_CLIENT_ID` | (required) | Public OIDC client id |
| `OAUTH_SCOPE` | `openid profile offline_access` | Requested scopes |

### 2.3 External tools (optional)
| Env var | Default | Purpose |
|---|---|---|
| `SWAGGER_URL` | `/swagger/` | Swagger UI URL |
| `CHEF_URL` | `/chef` | FSH Chef URL |
| `CHEF_FHIR_VERSION` | `5.0.0` | FHIR version for Chef |
| `PLANT_UML_URL` | `/plantuml` | PlantUML server URL |
| `FML_EDITOR` | `/fml-editor` | FML editor URL |

### 2.4 SNOMED browsing links (optional)
| Env var | Default | Purpose |
|---|---|---|
| `SNOWSTORM_URL` | `` | Snowstorm URL used by the web UI |
| `SNOWSTORM_DAILY_BUILD_URL` | `` | Daily-build Snowstorm URL |
| `SNOMED_BROWSER_URL` | `` | SNOMED browser UI URL |
| `SNOMED_BROWSER_DAILY_BUILD_URL` | `` | Daily-build browser URL |

### 2.5 Languages / i18n (JSON values)
| Env var | Default | Purpose |
|---|---|---|
| `DEFAULT_LANGUAGE` | `en` | Default UI language |
| `UI_LANGUAGES` | `["en","et","lt","de","fr","nl","cs"]` | Selectable UI languages (JSON array) |
| `CONTENT_LANGUAGES` | = `UI_LANGUAGES` | Selectable content languages (JSON array) |
| `EXTRA_LANGUAGES` | `{}` | Extra language labels, e.g. `{"ar":{"en":"Arabic"}}` (JSON object) |

### 2.6 Feature toggles (JSON booleans)
| Env var | Default | Purpose |
|---|---|---|
| `EMBEDDED` | `false` | Embedded mode: no guest login, links open in same tab |
| `GUEST_DISABLED` | `false` | Disable guest access in the UI |

---

## 3. PostgreSQL — `pg.env`
| Env var | Default | Purpose |
|---|---|---|
| `POSTGRES_USER` | `postgres` | Superuser |
| `POSTGRES_PASSWORD` | `postgres` | Superuser password |
| `POSTGRES_DB` | `postgres` | Bootstrap database |

The init script creates the TermX database (`termx`) and roles: **`tx_admin`** (owner / Liquibase),
**`tx_app`** (runtime), **`tx_viewer`** (read-only). Their passwords come from `DB_ADMIN_PASSWORD` /
`DB_APP_PASSWORD` on the server side — keep them in sync.

---

## 4. Companion services (quick-start; all optional)

| Service | Key env vars | Notes |
|---|---|---|
| **FSH Chef** | — | `8500`, path `/chef` |
| **PlantUML** | `BASE_URL` (`plantuml`) | `8501`, path `/plantuml` |
| **FML editor** | `BASE_HREF` (`/fml-editor/`) | `8502` |
| **Swagger UI** | `CONFIG_URL`, `OAUTH_CLIENT_ID`, `OAUTH_REALM`, `BASE_URL` | `8000`, path `/swagger` |
| **MinIO** | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` (+ app user `BOB_MINIO_*`) | `9100` API / `9101` console; data dir persists credentials |
| **Keycloak** | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `KC_DB*`, `KC_HOSTNAME`, `KC_PROXY` | Use external Postgres + set `KC_*` for production |
| **Snowstorm + Elasticsearch** | `ES_JAVA_OPTS` (`-Xms4g -Xmx4g`), Snowstorm heap `-Xmx4g` | Run from the `snomed/` compose; import RF2 via API |

---

## 5. Minimal production checklist

**Server (`server.env`):** `DB_URL`, `DB_APP_PASSWORD`, `DB_ADMIN_PASSWORD`, `OAUTH_JWKS_URL`,
and — if used — `BOB_MINIO_URL`/`BOB_MINIO_ACCESS_KEY`/`BOB_MINIO_SECRET_KEY` (import features),
`SNOWSTORM_URL` (+ branch/auth) for SNOMED, `KEYCLOAK_*` for user management, `SMTP_*` for email,
`MICRONAUT_SERVER_CORS_CONFIGURATIONS_UI_ALLOWED_ORIGINS` if web and API are on different origins.

**Web (`web.env`):** `OAUTH_ISSUER`, `OAUTH_CLIENT_ID`, `TERMX_API`, `BASE_HREF`.

**DB (`pg.env`):** set `POSTGRES_PASSWORD`; ensure `tx_admin`/`tx_app` passwords match the server.

Do **not** ship with `AUTH_MOCK_ENABLED=true`, default DB passwords (`test`), or default MinIO
credentials (`minio`/`minio123`).

---

## 6. Reverse-proxy paths (reference)

| Path | Upstream | Notes |
|---|---|---|
| `/` | termx-web | SPA |
| `/api/` | termx-server `:8200` | raise client max body size (≈600 MB for SNOMED import) |
| `/swagger` | swagger-ui `:8000` | |
| `/chef/` | fsh-chef `:8500` | |
| `/plantuml` | plantuml `:8501` | |
| `/fml-editor/` | fml-editor `:8502` | |
| `/minio-console/` | MinIO console `:9101` | WebSocket |

---

## 7. Caveats, gotchas & known divergences

### Variable names
- **MinIO: use `BOB_MINIO_*`.** `application.yml` declares `BOB_MINIO_URL` / `BOB_MINIO_ACCESS_KEY` /
  `BOB_MINIO_SECRET_KEY`, consistent with quick-start, the public install guide, and the large-import
  feature doc. The `BOB_` prefix keeps the app's access credentials distinct from the MinIO server's
  own root account (`MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`). The older unprefixed `MINIO_*` names
  are **no longer read**.
- **Snowstorm & Keycloak have no `${ENV}` placeholders.** `snowstorm.*` is hard-coded in
  `application.yml` (with sensible defaults); `keycloak.*` only appears in profile files
  (`application-demo.yml` / `application-dev.yml`). You still override them with `SNOWSTORM_*` /
  `KEYCLOAK_*` env vars (Micronaut binds env → property). Because `keycloak.*` has **no base
  default**, the user-management features effectively **require** the four `KEYCLOAK_*` vars when used.

### Config-parsing gotchas
- **Colons in URL defaults are backticked in YAML, not in env.** A default like
  `` ${BOB_MINIO_URL:`http://localhost:9000`} `` is backticked so Micronaut doesn't treat `:` as the
  value/default delimiter. When you *set* the env var, give the plain URL — no backticks.
- **Web JSON-typed vars must be valid JSON.** `UI_LANGUAGES`, `CONTENT_LANGUAGES`, `EXTRA_LANGUAGES`,
  `EMBEDDED`, `GUEST_DISABLED` are parsed as JSON; an empty value is dropped and the built-in default
  applies, and a malformed value silently falls back.
- **`env.js` is served `no-store`.** Changing `web.env` takes effect on container **restart** (the
  entrypoint re-runs `envsubst`) — no image rebuild needed.

### Operational gotchas
- **Keep DB passwords in sync.** `DB_APP_PASSWORD` / `DB_ADMIN_PASSWORD` (server) must match the
  `tx_app` / `tx_admin` role passwords created by the Postgres init, or the server can't connect /
  Liquibase fails.
- **MinIO root password is sticky.** MinIO persists root credentials in its data dir; changing
  `MINIO_ROOT_PASSWORD` after first start is ignored unless you wipe the data volume. The per-app
  `BOB_MINIO_*` user must match what the init container created.
- **No object storage ⇒ import endpoints fail (503).** SNOMED/LOINC archive import and wiki file
  storage require MinIO; without `BOB_MINIO_*` those features are unavailable.
- **Request-size limits.** SNOMED International RF2 archives are ≈549 MB; the default
  `micronaut.server.max-request-size` / `multipart.max-file-size` is **600 MB** (`629145600`). Raise
  both these and the reverse-proxy body limit if you import larger archives, or uploads fail.
- **OOM ⇒ container restart by design.** The image runs with `-XX:+ExitOnOutOfMemoryError` and dumps
  to `/app/logs`, so Docker's restart policy recovers it — size `JAVA_OPTS -Xmx` for your workload.
  **Set a container memory limit** (`mem_limit` / `--memory`) at least as large as `-Xmx` plus
  headroom: with no cap the JVM's effective ceiling is the host's physical RAM, so the heap can grow
  well past `-Xmx` under heavy load (large Snowstorm RF2 imports running alongside FHIR CodeSystem
  caching) before the flag trips. A cap makes the restart predictable and protects co-located services.
- **Server listens on `:8200`** in the container; the dev script's `TERMX_SERVER_PORT` does not apply
  to the published image.

### Security — never ship defaults
Default DB passwords (`test`), default MinIO creds (`minio`/`minio123`, or quick-start
`bob`/`bobobobo`), `AUTH_MOCK_ENABLED=true`, and dev tokens (`auth.dev.allowed` → `Bearer yupi`) are
for local/dev only. Set real secrets and disable mock/dev auth in production.

### Source-of-truth & staleness
- **Authoritative defaults = the current `termx-app/src/main/resources/application*.yml`.**
- The **public installation guide** (`knowledge-base/installation-guide.md`) predates the
  Kodality → TermX rename: it shows `docker.kodality.com/...` images, a `termserver` DB name, and
  `auth.kodality.dev` URLs. The **variable names** are still valid; the image registry, DB name and
  example URLs are **not** — prefer the **`termx-quick-start`** stack as the current working reference.
- Dev-only auth knobs not tabled above: `auth.dev.allowed` (enables the `Bearer yupi` token) and
  `auth.dev.yupi.privileges` (default `*.*.*`).

---

## 8. Related docs
- [`snomed-import-management.md`](snomed-import-management.md), [`loinc-import-management.md`](loinc-import-management.md) — SNOMED/LOINC archive import, storage, retention.
- [`smtp-email-support.md`](smtp-email-support.md) — email setup.
- [`mock-auth.md`](mock-auth.md) — mock auth provider.
- [`codesystem-import.md`](codesystem-import.md) — code system import logic.
- [`value-set-expand.md`](value-set-expand.md) — value set expansion/snapshot.
- [`termx-quick-start`](https://github.com/termx-health/termx-quick-start) — ready-to-run compose stack with sample `*.env` files.
