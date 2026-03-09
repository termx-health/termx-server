# TermX Server — Package Architecture

## Overview

TermX Server uses two Java package root namespaces:

| Namespace | Purpose |
|-----------|---------|
| `org.termx.*` | Active namespace for all feature modules and new code |
| `com.kodality.termx.*` | Foundation libraries: `termx-api` (shared DTOs) and `termx-core` (infrastructure) |

All new code must use `org.termx.*`. The `com.kodality.termx.*` namespace is frozen — only `termx-api` and `termx-core` remain there and are not expected to be renamed due to their central role as stable contract libraries.

---

## Module Map

### Foundation (com.kodality.termx.*)

These two modules define the shared contract and infrastructure consumed by all feature modules. They intentionally remain under `com.kodality.termx.*`.

| Gradle module | Package | Description |
|---------------|---------|-------------|
| `termx-api` | `com.kodality.termx.ts.*` | Terminology Service API DTOs (CodeSystem, ValueSet, MapSet, Concept, …) |
| `termx-api` | `com.kodality.termx.sys.*` | System-level API DTOs (Space, Release, Provenance, …) |
| `termx-api` | `com.kodality.termx.wiki.page.*` | Wiki page DTOs (Page, PageContent, PageRelation, PageTag, …) |
| `termx-api` | `com.kodality.termx.snomed.*` | SNOMED CT API DTOs |
| `termx-api` | `com.kodality.termx.ucum.*` | UCUM / Measurement Unit DTOs |
| `termx-api` | `com.kodality.termx.modeler.structuredefinition.*` | Structure Definition DTOs |
| `termx-api` | `com.kodality.termx.auth.*` | Authentication API types |
| `termx-core` | `com.kodality.termx.core.*` | Infrastructure: auth, FHIR base, provenance, space/github/server services |

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
| `termx-app` | `com.kodality.termx` | Application entry point, Micronaut bootstrap, OpenAPI aggregation |

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
  ├── termx-api       (com.kodality.termx.ts.*, sys.*, wiki.page.*, ...)
  ├── termx-core      (com.kodality.termx.core.*)
  └── other feature modules (when explicitly declared in build.gradle)

termx-core
  └── termx-api

termx-api
  └── (no internal dependencies)
```

Cross-module dependencies between feature modules are allowed only when declared in `build.gradle` — e.g., `ucum` depends on `terminology` for code system administration; `wiki` depends on `bob` for attachment storage.

---

## Migration Status

The table below tracks which modules have completed migration from the legacy `com.kodality.termx.*` namespace to `org.termx.*`.

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
| `termx-api` | 🔒 Stays `com.kodality.termx.*` (public contract) |
| `termx-core` | 🔒 Stays `com.kodality.termx.core.*` (infrastructure) |
| `termx-app` | 🔒 Stays `com.kodality.termx` (bootstrap entry point) |
