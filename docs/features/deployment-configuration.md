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
> (`` ${MINIO_URL:`http://localhost:9000`} ``) because Micronaut treats `:` as the value/default
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

> The base `application.yml` placeholders are `MINIO_URL` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`;
> deployments and the feature docs use the `BOB_MINIO_*` names. Both bind to the same `bob.minio.*`
> properties — prefer `BOB_MINIO_*`.

### 1.6 Large terminology import (SNOMED + LOINC archives) — see [`large-terminology-import.md`](large-terminology-import.md)
| Env var / property | Default | Purpose |
|---|---|---|
| `SNOMED_DELTA_GENERATOR_JAR` | `/opt/delta-generator-tool.jar` | Path to the delta-generator jar in the container |
| `termx.terminology-archive.bucket` | `terminology-archives` | MinIO bucket for stored archives (auto-created) |
| `termx.terminology-archive.retention-per-edition` | `3` | Archives kept per edition before cleanup |
| `termx.snomed.delta-generator.timeout-seconds` | `1800` | Hard cap on the delta-generator subprocess |
| `micronaut.server.max-request-size` | `1610612736` (1.5 GB) | Max multipart upload (SNOMED Intl ≈ 1 GB) |

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
| `/api/` | termx-server `:8200` | raise client max body size (≈1.5 GB for SNOMED import) |
| `/swagger` | swagger-ui `:8000` | |
| `/chef/` | fsh-chef `:8500` | |
| `/plantuml` | plantuml `:8501` | |
| `/fml-editor/` | fml-editor `:8502` | |
| `/minio-console/` | MinIO console `:9101` | WebSocket |

---

## 7. Related docs
- [`large-terminology-import.md`](large-terminology-import.md) — SNOMED/LOINC archive import, storage, retention.
- [`smtp-email-support.md`](smtp-email-support.md) — email setup.
- [`mock-auth.md`](mock-auth.md) — mock auth provider.
- [`codesystem-import.md`](codesystem-import.md) — code system import logic.
- [`value-set-expand.md`](value-set-expand.md) — value set expansion/snapshot.
- [`termx-quick-start`](https://github.com/termx-health/termx-quick-start) — ready-to-run compose stack with sample `*.env` files.
