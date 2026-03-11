# Vendoring Status

This document tracks the status of vendored external dependencies in termx-server.

## ✅ Successfully Vendored

### Kodality Commons (15 modules)
**Source**: `/Users/igor/source/helex/kodality-commons` and `/Users/igor/source/helex/kodality-commons-micronaut`  
**Location**: `kodality-commons/`  
**Status**: ✅ Complete - All modules vendored and building successfully

#### Core Modules (8):
- commons-model
- commons-util
- commons-db-core
- commons-db (merged with commons-db-bean)
- commons-http-client
- commons-csv
- commons-zmei
- commons-cache

#### Micronaut Modules (7):
- commons-micronaut
- commons-micronaut-pg
- commons-micronaut-drools
- commons-util-spring
- commons-permcache
- commons-sequence
- commons-tenant

**Maven Dependencies Eliminated**: All `com.kodality.commons:*` artifacts

---

### Zmei (4 modules)
**Source**: `/Users/igor/source/helex/zmei`  
**Location**: `zmei/`  
**Status**: ✅ Complete - All modules vendored and building successfully

#### Modules:
- zmei-fhir
- zmei-fhir-jackson
- zmei-fhir-client
- zmei-cds

**Maven Dependencies Eliminated**: All `com.kodality.zmei:*` artifacts

---

## ❌ Not Vendored (Still Using Maven)

### Kefhir
**Source**: `/Users/igor/source/helex/kefhir`  
**Status**: ❌ Blocked - HAPI version mismatch  
**Reason**: Kefhir was built with HAPI 6.8.5, termx-server uses HAPI 7.6.0. API changes in HAPI require code modifications to kefhir source.

#### Still Using Maven Artifacts:
- `com.kodality.kefhir:kefhir-core:R5.4.3`
- `com.kodality.kefhir:fhir-rest:R5.4.3`
- `com.kodality.kefhir:validation-profile:R5.4.3`
- `com.kodality.kefhir:openapi:R5.4.3`

**Next Steps**: Either:
1. Update kefhir source code to work with HAPI 7.6.0, or
2. Keep kefhir as an external Maven dependency (current approach)

---

## Build Configuration

### Dependency Substitution Files:
- `kodality-commons-dependencies.gradle.kts` - Substitutes all commons modules
- `zmei-dependencies.gradle.kts` - Substitutes all zmei modules

### Gradle Properties Added:
- `kodalityCommonsVersion=2-SNAPSHOT`
- `hapiVersion=7.6.0`
- `ehcacheVersion=3.10.8`

### Build Success:
```bash
./gradlew clean assemble --no-daemon
# BUILD SUCCESSFUL in 23s
# 189 actionable tasks: 111 executed, 38 from cache, 40 up-to-date
```

---

## External Maven Dependencies Remaining

The following external repositories are still referenced but only for kefhir and other third-party libraries:

```groovy
repositories {
    mavenCentral()
    maven { url = uri("https://kexus.kodality.com/repository/maven-public/") }  // Only for kefhir now
}
```

**Note**: `kexus.kodality.com` is now ONLY used for kefhir artifacts. All kodality-commons and zmei dependencies have been eliminated.

---

## Summary

- **19 modules successfully vendored** (15 commons + 4 zmei)
- **Zero external dependencies** on `com.kodality.commons` and `com.kodality.zmei`
- **Build time**: ~23s (clean build)
- **Docker builds**: Will now use vendored code consistently across all environments

This vendoring eliminates the `NoSuchMethodError` issues that were occurring in Docker due to SNAPSHOT version inconsistencies between local and CI/CD environments.
