# Kodality Commons (Vendored)

This directory contains a vendored copy of kodality-commons from:
https://gitlab.com/kodality/kodality-commons

## Version

2-SNAPSHOT (commit 8f0bdd3, March 2026)

## Reason for Vendoring

The Maven repository's SNAPSHOT versions were inconsistent between local development and CI/CD, causing `NoSuchMethodError` in Docker deployments. Vendoring ensures consistent builds across all environments.

## Modules Included

- **commons-model**: Core data models
- **commons-util**: Utility classes  
- **commons-db-core**: Database core functionality
- **commons-db**: Main database module (includes merged commons-db-bean for Maven compatibility)
- **commons-http-client**: HTTP client utilities
- **commons-csv**: CSV processing
- **commons-zmei**: FHIR/ZMEI integration
- **commons-cache**: Caching utilities

## Important Notes

### Module Structure Change

The Maven artifact `com.kodality.commons:commons-db` includes code from BOTH `commons-db` and `commons-db-bean` modules merged together. To match this structure:

- **commons-db-bean sources were merged into commons-db**
- `BaseBeanRepository` methods are available via `BaseRepository`
- This avoids circular dependency issues in the build

### Dependencies Not Vendored

The following are still fetched from Maven (not in this repo):
- `commons-micronaut`
- `commons-micronaut-pg`

These are in a separate repository and work fine from Maven.

## Updates

To update to a newer version:

```bash
cd /Users/igor/source/helex/kodality-commons
git pull origin main

cd /Users/igor/source/termx/termx-server
rm -rf kodality-commons/commons-*
cp -r /Users/igor/source/helex/kodality-commons/commons-* kodality-commons/
# Merge commons-db-bean into commons-db
cp -r kodality-commons/commons-db-bean/src/main/java/* kodality-commons/commons-db/src/main/java/
rm -rf kodality-commons/commons-db-bean
# Update project references in build.gradle files
# Test build
./gradlew clean assemble
```

## License

MIT License (see original repository)
