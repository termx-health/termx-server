# TermX Server — Package Architecture

## Build System

| Component | Version | Notes |
|-----------|---------|-------|
| Gradle | 9.4.0 | Build automation with Kotlin DSL |
| Java | 25 (LTS) | Java toolchain auto-provisioning enabled |
| Shadow Plugin | 9.3.2 | Fat JAR packaging |
| Micronaut Plugin | 4.6.2 | Micronaut framework integration |
| Lombok | 1.18.42 | Annotation processing |

**Build files:** All build configuration uses Gradle Kotlin DSL (`.gradle.kts` format).

**Publishing:** Maven artifacts publish to [GitHub Packages](https://github.com/termx-health/termx-server/packages) as `org.termx:termx-server:x.y.z`.

**Java Toolchain:** Gradle automatically provisions JDK 25 using the Foojay Disco API if not installed locally.

---

## Overview

TermX Server uses two Java package root namespaces:

| Namespace | Purpose |
|-----------|---------|
| `org.termx.*` | All modules (API contracts, core infrastructure, and feature implementations) |

All code uses `org.termx.*` namespace. The migration from `com.kodality.termx.*` to `org.termx.*` has been completed for all modules including `termx-api` and `termx-core`.

---

## Module Map

### Foundation (org.termx.*)

These two modules define the shared contract and infrastructure consumed by all feature modules.

| Gradle module | Package | Description |
|---------------|---------|-------------|
| `termx-api` | `org.termx.ts.*` | Terminology Service API DTOs (CodeSystem, ValueSet, MapSet, Concept, …) |
| `termx-api` | `org.termx.sys.*` | System-level API DTOs (Space, Release, Provenance, …) |
| `termx-api` | `org.termx.wiki.*` | Wiki page DTOs (Page, PageContent, PageRelation, PageTag, …) |
| `termx-api` | `org.termx.snomed.*` | SNOMED CT API DTOs |
| `termx-api` | `org.termx.ucum.*` | UCUM / Measurement Unit DTOs |
| `termx-api` | `org.termx.modeler.*` | Structure Definition DTOs |
| `termx-api` | `org.termx.auth.*` | Authentication API types |
| `termx-api` | `org.termx.commons.*` | Common utilities and interfaces |
| `termx-api` | `org.termx.sequence.*` | Sequence DTOs |
| `termx-api` | `org.termx.observationdefintion.*` | Observation Definition DTOs |
| `termx-core` | `org.termx.core.*` | Infrastructure: auth, FHIR base, provenance, space/github/server services |

### Feature Modules (org.termx.*)

All business logic lives here. Each Gradle subproject maps to a single top-level package.

| Gradle module | Package | Description |
|---------------|---------|-------------|
| `task` | `org.termx.task` | Task management domain model, service, and REST controller |
| `task-taskforge` | `org.termx.taskforge` | TaskForge integration (maps tasks to external workflow engine) |
| `terminology` | `org.termx.terminology` | Core terminology services: code systems, value sets, map sets, FHIR adapters, file importers |
| `ucum` | `org.termx.ucum` | UCUM unit-of-measure service: measurement units, FHIR integration, essence store |
| `wiki` | `org.termx.wiki` | Wiki module: pages, content, comments, tags, templates, links, attachments |
| `bob` | `org.termx.bob` | Binary Object Store (BOB) — general-purpose file/attachment storage |
| `modeler` | `org.termx.modeler` | Structure Definition and Transformation Definition modelling |
| `snomed` | `org.termx.snomed` | SNOMED CT integration (Snowstorm client, RF2 import/export, translations) |
| `observation-definition` | `org.termx.observationdefinition` | FHIR ObservationDefinition resource support |
| `implementation-guide` | `org.termx.implementationguide` | FHIR Implementation Guide authoring and publishing |
| `edition-int` | `org.termx.editionint` | International terminology edition extensions |
| `edition-est` | `org.termx.editionest` | Estonian national edition extensions |
| `edition-uzb` | `org.termx.editionuzb` | Uzbek national edition extensions (IchiUz) |
| `uam` | `org.termx.uam` | User and Access Management |
| `termx-app` | `org.termx` | Application entry point, Micronaut bootstrap, OpenAPI aggregation |

---

## Package Naming Convention

```
org.termx.<module>.<subpackage>
```

**Examples:**

```
org.termx.terminology.terminology.codesystem.CodeSystemService
org.termx.wiki.page.PageController
org.termx.snomed.integration.SnomedController
org.termx.task.TaskService
org.termx.ucum.measurementunit.MeasurementUnitRepository
```

Sub-packages within a module follow standard layering:
- `*.controller` / root-level `*Controller.java` — REST endpoints
- `*.service` / `*Service.java` — business logic
- `*.repository` / `*Repository.java` — database access
- `*.mapper` / `*Mapper.java` — data transformation
- `*.fhir.*` — FHIR-specific adapters and mappers
- `*.ts.*` — Terminology Server provider implementations (CodeSystemProvider, ValueSetExpandProvider, …)
- `*.task.*` — Task interceptors for the module
- `*.api.*` — Extension points / SPI interfaces

---

## Dependency Rules

```
termx-app
  ├── all feature modules (org.termx.*)
  └── termx-core

feature modules (org.termx.*)
  ├── termx-api       (org.termx.ts.*, sys.*, wiki.*, snomed.*, ucum.*, modeler.*, auth.*, ...)
  ├── termx-core      (org.termx.core.*)
  └── other feature modules (when explicitly declared in build.gradle)

termx-core
  └── termx-api

termx-api
  └── (no internal dependencies)
```

Cross-module dependencies between feature modules are allowed only when declared in `build.gradle.kts` — e.g., `ucum` depends on `terminology` for code system administration; `wiki` depends on `bob` for attachment storage.

---

## Build Configuration

### Gradle Kotlin DSL

All build files use Gradle Kotlin DSL for type-safe configuration:
- `settings.gradle.kts` — Project structure and toolchain resolver
- `build.gradle.kts` — Root configuration with Java toolchain and publishing
- `[module]/build.gradle.kts` — Module-specific dependencies

### Gradle tasks

| Task | Description | Used in CI |
|------|-------------|------------|
| `clean` | Removes build outputs | Yes |
| `assemble` | Compiles and builds JARs (no tests) | Yes |
| `check` | Runs tests and PMD; does **not** run SpotBugs | Yes |
| `publish` | Publishes Maven artifacts to GitHub Packages | Yes |
| `run` | Runs the application (from `termx-app`; use `:termx-app:run`) | No |
| `shadowJar` | Builds the fat JAR for `termx-app` | No (Docker build uses context) |
| `spotbugsCheck` | Runs SpotBugs on all projects; **manual only**, not part of `check` | No |

**Verification:** `check` runs unit/integration tests and PMD. SpotBugs is excluded from `check` to keep CI and Docker builds fast; run `./gradlew spotbugsCheck` when you want static analysis.

**Application:** To run the server locally from the root: `./gradlew :termx-app:run`. Optional: `-Pdev` for dev auth, `-Pdebug=5005` for remote debugging.

**List tasks:** `./gradlew tasks` (all), `./gradlew tasks --group=verification` (verification only).

### Maven Coordinates

**Group ID:** `org.termx`

**Modules published:**
- `org.termx:termx-api` — Shared DTOs and contracts
- `org.termx:termx-core` — Infrastructure and common services
- `org.termx:terminology` — Terminology module
- `org.termx:termx-app` — Main application (shadow JAR)
- All other feature modules

### Deployment

**Docker Image:** `eclipse-temurin:25-jre`
- Published to GitHub Container Registry: `ghcr.io/termx-health/termx-server`
- Multi-platform: `linux/amd64`, `linux/arm64`

**CI/CD:** GitHub Actions
- Builds on push to `main` or version tags
- Publishes Maven artifacts and Docker images automatically

---

## Migration Status

All modules have completed migration to the `org.termx.*` namespace.

| Module | Status |
|--------|--------|
| `task` | ✅ `org.termx.task` |
| `task-taskforge` | ✅ `org.termx.taskforge` |
| `terminology` | ✅ `org.termx.terminology` |
| `ucum` | ✅ `org.termx.ucum` |
| `wiki` | ✅ `org.termx.wiki` |
| `bob` | ✅ `org.termx.bob` |
| `modeler` | ✅ `org.termx.modeler` |
| `snomed` | ✅ `org.termx.snomed` |
| `observation-definition` | ✅ `org.termx.observationdefinition` |
| `implementation-guide` | ✅ `org.termx.implementationguide` |
| `edition-int` | ✅ `org.termx.editionint` |
| `edition-est` | ✅ `org.termx.editionest` |
| `edition-uzb` | ✅ `org.termx.editionuzb` |
| `uam` | ✅ `org.termx.uam` |
| `termx-api` | ✅ `org.termx.*` (public contract DTOs) |
| `termx-core` | ✅ `org.termx.core.*` (infrastructure) |
| `termx-app` | ✅ `org.termx.*` (application bootstrap) |
| `termx-client` | ✅ `org.termx.ts.*` (HTTP client library) |
