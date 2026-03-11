# Kodality Commons Vendoring - Blocker

## Root Cause

The Maven artifact `com.kodality.commons:commons-db:1-SNAPSHOT` contains merged classes from BOTH:
- `commons-db` module (BaseRepository with getBean/getBeans methods)
- `commons-db-bean` module (PgBeanProcessor, BaseBeanRepository)

But in the Git source code, these are split into separate modules with circular dependency:
- `commons-db-bean` depends on `commons-db`
- Maven packaging merges BaseBeanRepository methods INTO BaseRepository

## Failed Approaches

1. ❌ Add commons-db-bean as dependency of commons-db → Circular dependency
2. ❌ Merge BaseBeanRepository methods into BaseRepository → Requires PgBeanProcessor import → Circular dependency
3. ❌ Add commons-db-bean to all termx modules → Doesn't fix BaseRepository method resolution

## Solutions

### Option 1: Build Shaded JAR (Recommended)
Configure Gradle to build a single fat JAR for commons that includes all classes:
```gradle
// In kodality-commons/commons-db/build.gradle
plugins {
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

shadowJar {
    configurations = [project.configurations.runtimeClasspath]
    dependencies {
        include(project(':kodality-commons:commons-db-bean'))
    }
}
```

### Option 2: Remove Module Split
Merge commons-db-bean classes back into commons-db:
```bash
cp -r kodality-commons/commons-db-bean/src/main/java/* kodality-commons/commons-db/src/main/java/
# Update commons-db/build.gradle dependencies
# Remove commons-db-bean module from settings
```

### Option 3: Wait for GitHub Actions Build
The simplest short-term solution - let GitHub Actions build with:
- `--refresh-dependencies` (forces fresh SNAPSHOT download)
- `--no-daemon` (prevents caching)

Commit `9d8721ac` has these flags configured.

## Recommendation

Use **Option 3** now (5-minute wait), then complete **Option 1 or 2** for long-term stability in a dedicated refactoring session.

## Rollback Current Changes

```bash
cd /Users/igor/source/termx/termx-server
git reset --hard HEAD
git clean -fd
rm -rf kodality-commons/
```

This restores to commit `9d8721ac` with GitHub Actions fixes.
