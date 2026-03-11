# Kodality Maven Repository Restriction

## Purpose

To ensure that all vendored `kodality-commons` and `zmei` modules are compiled from source in this repository (not downloaded from Maven), the Kodality Maven repository access is explicitly restricted.

## Configuration

In `build.gradle.kts`, the Kodality Maven repository is configured with content filtering:

```kotlin
repositories {
    mavenCentral()
    maven { 
        url = uri("https://kexus.kodality.com/repository/maven-public/")
        content {
            includeGroup("com.kodality.kefhir")
        }
    }
}
```

## What This Does

- **Allows**: `com.kodality.kefhir:*` to be downloaded from kexus.kodality.com
- **Blocks**: `com.kodality.commons:*`, `com.kodality.zmei:*`, and `com.kodality.taskflow:*` from being downloaded from Maven
- **Forces**: Gradle to use the vendored source code in `kodality-commons/` and `zmei/` directories
- **Note**: taskForge is used instead of taskflow

## Why This Matters

Without this restriction, Gradle might:
1. Download cached SNAPSHOT versions from Maven that don't match our vendored source
2. Bypass the `kodality-commons-dependencies.gradle.kts` and `zmei-dependencies.gradle.kts` substitution rules
3. Cause the `NoSuchMethodError` in Docker deployments when incompatible Maven artifacts are used

## Verification

To verify vendored code is being used:

```bash
# Build the project
./gradlew clean :kodality-commons:commons-db:assemble

# Check that PgBeanProcessor.class is in the JAR
jar -tf kodality-commons/commons-db/build/libs/commons-db-*.jar | grep PgBeanProcessor

# Verify no kodality Maven downloads occurred
./gradlew :terminology:dependencies --configuration compileClasspath | grep kodality
# Should show project dependencies like: project :kodality-commons:commons-db
# NOT Maven coordinates like: com.kodality.commons:commons-db:1-SNAPSHOT
```

## GitHub Actions

The GitHub Actions workflow (`build.yml`) has been simplified:
- Removed `cache-disabled: true` from setup-gradle (not needed with content filtering)
- Removed explicit Maven cache clearing (not needed)
- Kept verification step to ensure PgBeanProcessor is in JARs

## Date

Created: March 11, 2026
