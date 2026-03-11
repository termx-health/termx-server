# Zmei (Vendored)

This directory contains a vendored copy of the Zmei FHIR library from:
/Users/igor/source/helex/zmei

## Version

R5-SNAPSHOT (March 2026)

## Reason for Vendoring

The Maven repository's SNAPSHOT versions were inconsistent between local development and CI/CD. Vendoring ensures consistent builds across all environments and eliminates dependency on external Maven repositories.

## Modules Included

- **zmei-fhir**: Simple and minimalistic FHIR reference library (core models)
- **zmei-fhir-jackson**: FHIR models Jackson serializer/deserializer
- **zmei-fhir-client**: Simple and minimalistic FHIR client
- **zmei-cds**: Clinical Decision Support (CDS) integration

## Dependencies

All zmei modules have been vendored and use internal project dependencies (`:zmei:*`).

External dependencies (standard libraries) are still resolved from Maven Central:
- Jackson (core, annotations, datatype-jsr310)
- Test dependencies (JUnit, Commons IO, etc.)

## Updates

To update to a newer version:

```bash
# Update zmei source
cd /Users/igor/source/helex/zmei
git pull origin main

# Copy to termx-server
cd /Users/igor/source/termx/termx-server
rm -rf zmei/zmei-*
cp -r /Users/igor/source/helex/zmei/zmei-* zmei/

# Update project references in build.gradle files to use :zmei: prefix
# Test build
./gradlew clean assemble
```

## License

MIT License (see original repository)
