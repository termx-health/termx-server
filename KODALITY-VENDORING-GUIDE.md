# Kodality Dependencies Vendoring Guide

## Overview

This project vendors `kodality-commons` and `zmei` libraries as source code, while keeping `kefhir` as a Maven dependency.

## Why Vendoring?

The `kodality-commons:commons-db` library from Maven (`1-SNAPSHOT`, `1.9`, etc.) **does not** include the `registerColumnHandler()` method in `org.apache.commons.dbutils.BeanProcessor`, which is required by `PgBeanProcessor`. This caused `NoSuchMethodError` in production Docker deployments.

## Critical Discovery: JAR Conflict

### The Problem

Even after vendoring, the `NoSuchMethodError` persisted because the final JAR contained **TWO versions** of `BeanProcessor.class`:

```
12723 bytes - vendored version WITH registerColumnHandler()  
12088 bytes - standard commons-dbutils:1.8.1 WITHOUT registerColumnHandler()
```

The JVM was loading the wrong one at runtime!

### The Solution

In `build.gradle.kts`, we **globally exclude** the standard `commons-dbutils:1.8.1` and replace it with our vendored version:

```kotlin
dependencies {
    modules {
        module("commons-dbutils:commons-dbutils") {
            replacedBy("org.termx:commons-db", "using vendored version with custom modifications")
        }
    }
}
```

## Repository Configuration

The Kodality Maven repository is restricted to **only** serve `kefhir` artifacts:

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

This forces Gradle to use:
- ✅ Vendored source for `com.kodality.commons.*`
- ✅ Vendored source for `com.kodality.zmei.*`
- ✅ Maven artifacts for `com.kodality.kefhir.*` only

## Vendored Modules

### kodality-commons (15 modules)
Located in `kodality-commons/`:
- commons-model
- commons-util
- commons-db-core
- commons-db (includes modified `org.apache.commons.dbutils.BeanProcessor`)
- commons-http-client
- commons-csv
- commons-zmei
- commons-cache
- commons-micronaut
- commons-micronaut-pg
- commons-micronaut-drools
- commons-util-spring
- commons-permcache
- commons-sequence
- commons-tenant

### zmei (4 modules)
Located in `zmei/`:
- zmei-fhir
- zmei-fhir-jackson
- zmei-fhir-client
- zmei-cds

## Verification

To verify the fix locally:

```bash
# Build the project
./gradlew clean :termx-app:assemble --no-daemon

# Check that only ONE BeanProcessor exists in the JAR
unzip -l termx-app/build/libs/termx-app-*-all.jar | grep "org/apache/commons/dbutils/BeanProcessor.class"
# Should show only ONE entry (12723 bytes)

# Verify commons-dbutils is replaced
./gradlew :termx-app:dependencies --configuration runtimeClasspath | grep commons-dbutils
# Should show: commons-dbutils:1.8.1 -> project :kodality-commons:commons-db
```

## Docker Build Verification

The GitHub Actions workflow verifies that:
1. PgBeanProcessor.class exists in the final JAR
2. BeanProcessor.class (with registerColumnHandler) exists in the final JAR

## Date

Created: March 11, 2026
Fixed conflict: March 11, 2026 (commit bf63d293)
