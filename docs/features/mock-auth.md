# Mock Authentication

## Overview

Mock authentication allows running TermX locally without an SSO/OAuth provider (e.g., Keycloak).
Users and their privileges are defined in JSON files shipped with the application. This is
useful for local development, manual QA testing, and demo environments.

## Quick start

1. Start the application with the `local` profile (mock auth is enabled in `application-local.yml`).
2. All requests without an `Authorization` header are authenticated as the default user (`admin`).
3. To switch users, add the header `Authorization: Bearer <profile-key>`.

```bash
# Authenticated as admin (default)
curl http://localhost:8200/api/tm/tasks

# Authenticated as editor1
curl -H "Authorization: Bearer editor" http://localhost:8200/api/tm/tasks

# Authenticated as publisher1
curl -H "Authorization: Bearer publisher" http://localhost:8200/api/tm/tasks

# Authenticated as viewer1 (no task list access)
curl -H "Authorization: Bearer viewer" http://localhost:8200/api/tm/tasks
```

## Configuration

### Properties

| Property                     | Env variable             | Default            | Description                                                   |
|------------------------------|--------------------------|--------------------|---------------------------------------------------------------|
| `auth.mock.enabled`          | `AUTH_MOCK_ENABLED`      | `false`            | Enables mock authentication                                   |
| `auth.mock.default-user`     | `AUTH_MOCK_DEFAULT_USER` | `admin`            | Profile key used when no `Authorization` header is present    |
| `auth.mock.users-file`       | `AUTH_MOCK_USERS_FILE`   | `mock/users.json`  | Classpath path to the JSON file with user definitions         |

### Enabling mock auth

**Option A** -- use the `local` Micronaut profile (already configured):

```bash
MICRONAUT_ENVIRONMENTS=local ./gradlew :termx-app:run
```

**Option B** -- set the property directly:

```bash
AUTH_MOCK_ENABLED=true ./gradlew :termx-app:run
```

### Switching to the demo user suite

```bash
AUTH_MOCK_USERS_FILE=mock/users-demo.json ./gradlew :termx-app:run
```

Or in `application-local.yml`:

```yaml
auth:
  mock:
    enabled: true
    users-file: mock/users-demo.json
```

## Bundled user suites

### mock/users.json (default, local development)

| Profile key  | Username     | Privileges                                                                          |
|--------------|--------------|-------------------------------------------------------------------------------------|
| `admin`      | `admin`      | `*.*.*` (full access)                                                               |
| `publisher`  | `publisher1` | `*.*.view`, `*.Task.publish`, `*.CodeSystem.publish`, `*.ValueSet.publish`, `*.MapSet.publish` |
| `editor`     | `editor1`    | `*.*.view`, `*.Task.edit`, `*.CodeSystem.edit`, `*.ValueSet.edit`, `*.MapSet.edit`   |
| `editor2`    | `editor2`    | `*.*.view`, `*.Task.edit`, `icd-10.CodeSystem.edit`, `disorders.ValueSet.edit`       |
| `viewer`     | `viewer1`    | `*.*.view`                                                                          |

### mock/users-demo.json (demo/showcase)

| Profile key          | Username         | Role description                                        |
|----------------------|------------------|---------------------------------------------------------|
| `admin`              | `demo-admin`     | Full access                                             |
| `terminology-manager`| `demo-tm`        | Can publish all resource types                          |
| `publisher`          | `demo-publisher` | Can publish specific resources (icd-10, icd-11, etc.)   |
| `editor`             | `demo-editor`    | Can edit specific resources                             |
| `reviewer`           | `demo-reviewer`  | Can edit icd-10 only                                    |
| `viewer`             | `demo-viewer`    | View-only                                               |
| `guest`              | `guest`          | View-only                                               |

## Testing task access control

The mock users are designed to test the TaskForge privilege-based access control.
Here is how each role behaves when querying `GET /api/tm/tasks`:

| Role      | Header                              | Tasks visible                                                 |
|-----------|--------------------------------------|---------------------------------------------------------------|
| Admin     | (none) or `Bearer admin`             | All tasks                                                     |
| Publisher | `Bearer publisher`                   | All tasks for resources they have publish access to            |
| Editor    | `Bearer editor`                      | Only tasks they created or are assigned to, for permitted resources |
| Viewer    | `Bearer viewer`                      | Empty result (no task list access)                             |

### Test scenario: create a task as editor, view as publisher

```bash
# Create a task as editor1
curl -X POST -H "Authorization: Bearer editor" \
     -H "Content-Type: application/json" \
     -d '{"title":"Review ICD-10 mapping","type":"concept-review","context":[{"type":"code-system","id":"icd-10"}]}' \
     http://localhost:8200/api/tm/tasks

# Query as publisher -- should see the task (publisher has publish access to icd-10)
curl -H "Authorization: Bearer publisher" http://localhost:8200/api/tm/tasks

# Query as viewer -- should get empty result
curl -H "Authorization: Bearer viewer" http://localhost:8200/api/tm/tasks

# Query as editor -- should see only own/assigned tasks
curl -H "Authorization: Bearer editor" http://localhost:8200/api/tm/tasks
```

## Custom user files

Create a JSON file with the following format:

```json
{
  "<profile-key>": {
    "username": "<display username>",
    "privileges": ["<privilege>", "..."]
  }
}
```

The `<profile-key>` is used in the `Authorization: Bearer <profile-key>` header.

### Privilege format

Privileges follow the TermX convention: `<resourceId>.<ResourceType>.<action>`.

| Segment        | Description                                  | Wildcard |
|----------------|----------------------------------------------|----------|
| `resourceId`   | Specific resource ID (e.g., `icd-10`) or `*` | `*`      |
| `ResourceType` | `CodeSystem`, `ValueSet`, `MapSet`, `Task`    | `*`      |
| `action`       | `view`, `edit`, `publish`                     | `*`      |

Examples:
- `*.*.*` -- full admin access
- `*.*.view` -- view all resources
- `*.Task.edit` -- edit tasks across all resources
- `icd-10.CodeSystem.publish` -- publish access to a specific code system

## How it works internally

`MockSessionProvider` extends `SessionProvider` with order `5` (runs before OAuth at `20`
and Guest at `30`). It is conditionally registered via `@Requires(property = "auth.mock.enabled", value = "true")`.

On startup, it reads the configured JSON file from the classpath and logs the number of
loaded users. On each request, it extracts the bearer token from the `Authorization` header,
looks up the matching profile, and returns a `SessionInfo` with the profile's username and
privileges. If no match is found, the default user profile is returned.

## Files

| File | Description |
|------|-------------|
| `termx-app/src/main/java/org/termx/auth/MockSessionProvider.java` | Provider implementation |
| `termx-app/src/main/resources/mock/users.json` | Default user suite (local dev) |
| `termx-app/src/main/resources/mock/users-demo.json` | Extended user suite (demo) |
| `termx-app/src/main/resources/application.yml` | Default config (`enabled: false`) |
| `termx-app/src/main/resources/application-local.yml` | Local override (`enabled: true`) |
