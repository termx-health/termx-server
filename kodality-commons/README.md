# Kodality Commons (Vendored)

This directory contains a vendored copy of kodality-commons from:
https://gitlab.com/kodality/kodality-commons

## Version
2-SNAPSHOT (commit 8f0bdd3)

## Reason for Vendoring
The Maven repository's SNAPSHOT versions were inconsistent between local
development and CI/CD, causing NoSuchMethodError in Docker deployments.
Vendoring ensures consistent builds across all environments.

## Modules Included
- commons-model: Core data models
- commons-util: Utility classes
- commons-db-core: Database core functionality
- commons-db-bean: Bean processing for PostgreSQL
- commons-db: Main database module
- commons-http-client: HTTP client utilities
- commons-csv: CSV processing
- commons-zmei: FHIR/ZMEI integration
- commons-cache: Caching utilities

## License
MIT License (see original repository)

## Updates
To update to a newer version, sync from the upstream GitLab repository.
