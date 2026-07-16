# Development Guide

## Multi-Version Project Structure

This project manages two Minecraft versions (`1.20.1` and `1.21.1`) on a single branch using Gradle tasks to switch between them.

### Directory Layout

```
gradle/
  active-version.properties   # Currently active version key
  versions/
    1.20.1.properties          # 1.20.1 dependency versions
    1.21.1.properties          # 1.21.1 dependency versions
versions/
  1.20.1/                      # 1.20.1-specific source code & resources
  1.21.1/                      # 1.21.1-specific source code & resources
src/
  main/java/                   # Shared main source code
  main/resources/              # Shared resources (assets, data, lang, etc.)
  client/java/                 # Shared client source code
  client/resources/            # Shared client resources
```

**Rules**:
- Files **identical** across versions → keep in `src/<dir>/`
- Files that **differ** between versions → move to `versions/<ver>/<dir>/`

### Common Tasks

```bash
# Version switching
./gradlew switchTo1201          # Switch to 1.20.1
./gradlew switchTo1211          # Switch to 1.21.1
./gradlew showActiveVersion     # Show current version

# Build & run
./gradlew runClient             # Run client for current version
./gradlew build                 # Build current version
./gradlew clean build           # Clean build (recommended after switching versions)

# File management
./gradlew makeVersionSpecific -Pfile=main/java/.../Foo.java
                                # Move a shared file into version-specific dirs
                                # Copies from src/ to versions/1.20.1/ and versions/1.21.1/
                                # then deletes the original from src/

./gradlew promoteToShared -Pfile=main/java/.../Bar.java
                                # Promote a version-specific file back to shared
                                # Fails if the two versions differ

./gradlew diffVersion -Pfile=main/java/.../Bar.java
                                # Show diff between the two version-specific files
```

The `-Pfile=` path is relative to project root and must include the source set prefix (`main/java/`, `client/java/`, etc.).

### Development Workflow

1. Switch to target version: `./gradlew switchTo1201`
2. Write code in your IDE (the entire project root)
3. Run directly: `./gradlew runClient` (no `clean` needed)
4. If you modify shared code in `src/`, the changes apply to both versions automatically
5. If a file needs different implementations per version, split it with `makeVersionSpecific`
6. If version-specific files become identical, merge them with `promoteToShared`

### Adding a New Version

1. Create `<ver>.properties` in `gradle/versions/` with the correct dependency versions
2. Add the version key to `ext.versionKeys` in `build.gradle`
3. Create the corresponding directory structure in `versions/<ver>/` and put version-specific files there
