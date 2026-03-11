# Kodality Commons (Vendored)

This directory contains vendored copies of:
1. kodality-commons from: https://gitlab.com/kodality/kodality-commons
2. kodality-commons-micronaut from: /Users/igor/source/helex/kodality-commons-micronaut

## Version

2-SNAPSHOT (commit 8f0bdd3, March 2026)

## Reason for Vendoring

The Maven repository's SNAPSHOT versions were inconsistent between local development and CI/CD, causing `NoSuchMethodError` in Docker deployments. Vendoring ensures consistent builds across all environments.

## Modules Included

### Core Modules (from kodality-commons)
- **commons-model**: Core data models
- **commons-util**: Utility classes  
- **commons-db-core**: Database core functionality with PostgreSQL functions
- **commons-db**: Main database module (includes merged commons-db-bean for Maven compatibility)
- **commons-http-client**: HTTP client utilities
- **commons-csv**: CSV processing
- **commons-zmei**: FHIR/ZMEI integration
- **commons-cache**: Caching utilities

### Micronaut Modules (from kodality-commons-micronaut)
- **commons-micronaut**: Micronaut base utilities and HTTP server integration
- **commons-micronaut-pg**: PostgreSQL integration for Micronaut
- **commons-micronaut-drools**: Drools (business rules) integration for Micronaut
- **commons-util-spring**: Spring framework utilities
- **commons-permcache**: Permission caching system
- **commons-sequence**: Sequence/ID generation management
- **commons-tenant**: Multi-tenancy support

## Important Notes

### Module Structure Change

The Maven artifact `com.kodality.commons:commons-db` includes code from BOTH `commons-db` and `commons-db-bean` modules merged together. To match this structure:

- **commons-db-bean sources were merged into commons-db**
- `BaseBeanRepository` methods are available via `BaseRepository`
- This avoids circular dependency issues in the build

### Dependencies Not Vendored

These vendored modules have NO remaining Maven dependencies on `com.kodality.commons` packages. All kodality-commons dependencies are now internal project dependencies.

External dependencies (standard libraries) are still resolved from Maven Central:
- Micronaut framework
- PostgreSQL drivers
- Apache Commons libraries
- etc.

## Updates

To update to a newer version:

```bash
# Update core commons modules
cd /Users/igor/source/helex/kodality-commons
git pull origin main

# Update micronaut modules
cd /Users/igor/source/helex/kodality-commons-micronaut
git pull origin main

# Copy to termx-server
cd /Users/igor/source/termx/termx-server
rm -rf kodality-commons/commons-*
cp -r /Users/igor/source/helex/kodality-commons/commons-* kodality-commons/
cp -r /Users/igor/source/helex/kodality-commons-micronaut/commons-* kodality-commons/

# Merge commons-db-bean into commons-db
cp -r kodality-commons/commons-db-bean/src/main/java/* kodality-commons/commons-db/src/main/java/
rm -rf kodality-commons/commons-db-bean

# Update project references in build.gradle files to use :kodality-commons: prefix
# Test build
./gradlew clean assemble
```

## License

MIT License (see original repositories)
