# Kodality Dependencies Vendoring Strategy

## Overview

This TermX server includes **vendored (copied) source code** from the following Kodality repositories to ensure stable, controlled dependencies:

1. **kodality-commons** → `/kodality-commons/`
2. **kodality-commons-micronaut** → `/kodality-commons/` (merged)
3. **zmei** → `/zmei/`

## Why Vendoring?

- **Stability**: Eliminates dependency on external SNAPSHOT Maven artifacts
- **Control**: Allows local modifications and fixes without waiting for upstream
- **Reliability**: Ensures builds work even if Kodality's Maven repository is unavailable

## Critical JAR Conflict: `commons-dbutils` BeanProcessor

### The Problem

The vendored `kodality-commons:commons-db` module contains **modified versions** of:
- `org.apache.commons.dbutils.BeanProcessor` (12723 bytes, includes `registerColumnHandler()` method)
- `org.apache.commons.dbutils.GenerousBeanProcessor` (1639 bytes, modified)

However, the standard `commons-dbutils:1.8.1` JAR (needed for all other classes like `ColumnHandler`, `QueryRunner`, etc.) also contains:
- `org.apache.commons.dbutils.BeanProcessor` (12088 bytes, **without** `registerColumnHandler()`)
- `org.apache.commons.dbutils.GenerousBeanProcessor` (1605 bytes, standard)

This creates a "JAR Hell" scenario where **both versions** would exist in the final `termx-app-*-all.jar`, and the JVM classloader may pick the **wrong** one at runtime.

### The Solution: Post-Processing Shadow JAR

We use a **post-processing step** in the `shadowJar` task to:

1. **Build the shadow JAR** with both versions (duplicates allowed)
2. **Remove ALL duplicates** using `zip -d`
3. **Re-add ONLY the vendored versions** using `zip -u`

This guarantees the final JAR contains **exactly one version** of each class (the vendored ones with `registerColumnHandler`).

### Configuration

#### `/kodality-commons/commons-db/build.gradle`
```gradle
api 'commons-dbutils:commons-dbutils:1.8.1'
```
- Exposes `commons-dbutils` transitively for all standard classes

#### `/termx-app/build.gradle.kts`
```kotlin
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    duplicatesStrategy = DuplicatesStrategy.WARN
    
    doLast {
        val jarFile = archiveFile.get().asFile
        val vendoredModule = project.project(":kodality-commons:commons-db")
        val vendoredClassDir = vendoredModule.layout.buildDirectory.dir("classes/java/main").get().asFile
        
        // Remove duplicates and restore vendored versions
        listOf("BeanProcessor.class", "GenerousBeanProcessor.class").forEach { className ->
            ProcessBuilder("zip", "-d", jarFile.absolutePath, "org/apache/commons/dbutils/${className}")
                .inheritIO().start().waitFor()
            ProcessBuilder("zip", "-u", jarFile.absolutePath, "org/apache/commons/dbutils/${className}")
                .directory(vendoredClassDir).inheritIO().start().waitFor()
        }
    }
}
```

### Verification

To verify the correct classes are used:

```bash
# Check ONLY ONE of each class exists
jar -tf termx-app/build/libs/termx-app-*-all.jar | grep "org/apache/commons/dbutils/BeanProcessor.class" | wc -l
# Should output: 1

jar -tf termx-app/build/libs/termx-app-*-all.jar | grep "org/apache/commons/dbutils/GenerousBeanProcessor.class" | wc -l
# Should output: 1

# Verify correct sizes (vendored versions)
unzip -l termx-app/build/libs/termx-app-*-all.jar | grep "BeanProcessor.class"
# Should show: 12723 bytes (NOT 12088)
# Should show: 1639 bytes (NOT 1605)
```

## Maven Repository Restrictions

Only **kefhir** dependencies are pulled from Kodality Maven (`https://kexus.kodality.com/repository/maven-public/`). All other `com.kodality.*` artifacts come from vendored source.

**Configuration** in `/build.gradle.kts`:
```kotlin
maven {
    url = uri("https://kexus.kodality.com/repository/maven-public/")
    content {
        includeGroup("com.kodality.kefhir")
    }
}
```

## Build Verification (CI)

GitHub Actions workflow verifies:
1. `PgBeanProcessor.class` exists in final JAR
2. Exactly **ONE** `BeanProcessor.class` (no duplicates)
3. Exactly **ONE** `GenerousBeanProcessor.class` (no duplicates)
4. `ColumnHandler.class` exists (from standard `commons-dbutils`)

See `.github/workflows/build.yml` for CI verification steps.

## Troubleshooting

### Error: `NoSuchMethodError: registerColumnHandler`
- **Cause**: Duplicate `BeanProcessor.class` in JAR - JVM loaded standard version (12088 bytes) without the method
- **Fix**: The `shadowJar` task's `doLast` block post-processes the JAR to remove duplicates
- **Verification**: 
  ```bash
  # Check for duplicates (should be 1 each, not 2)
  jar -tf termx-app/build/libs/termx-app-*-all.jar | grep -c "BeanProcessor.class"
  jar -tf termx-app/build/libs/termx-app-*-all.jar | grep -c "GenerousBeanProcessor.class"
  
  # Check sizes match vendored versions
  unzip -l termx-app/build/libs/termx-app-*-all.jar | grep "BeanProcessor.class"
  # Should show: 12723 bytes (NOT 12088)
  # Should show: 1639 bytes (NOT 1605)
  ```

### Error: `NoClassDefFoundError: ColumnHandler`
- **Cause**: Missing standard `commons-dbutils` classes (all standard classes accidentally excluded)
- **Fix**: Ensure `kodality-commons/commons-db/build.gradle` has `api 'commons-dbutils:commons-dbutils:1.8.1'`

## Future Improvements

If upstream Kodality releases stable (non-SNAPSHOT) versions of these libraries, consider reverting to Maven dependencies for easier updates.
