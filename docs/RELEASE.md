# Release Process

This document describes the release process for the spring-ai-resos project.

## Versioning Scheme

This project uses **date-based versioning** in the format `YYYY.MM.DD`:

| Format         | Example        | Description                          |
| -------------- | -------------- | ------------------------------------ |
| `YYYY.MM.DD`   | `2026.01.07`   | Standard release on January 7, 2026  |
| `YYYY.MM.DD.N` | `2026.01.07.1` | Patch/hotfix release on the same day |

## Prerequisites

Before creating a release:

- Ensure you have push access to the repository
- Verify all CI checks pass on main branch
- Use conventional commit messages for proper changelog generation

## Creating a Release

### 1. Ensure Clean Main Branch

```bash
git checkout main
git pull origin main
./mvnw clean verify
```

### 2. Create and Push Release Tag

```bash
# Determine version based on today's date
VERSION=$(date +%Y.%m.%d)

# If a release already exists for today, use patch notation
# VERSION=2026.01.07.1

# Create and push the tag
git tag -a "$VERSION" -m "Release $VERSION"
git push origin "$VERSION"
```

### 3. Automated Release Process

Pushing the tag triggers the GitHub Actions release workflow which:

1. **Validates** the tag format (YYYY.MM.DD or YYYY.MM.DD.N)
2. **Builds** all 6 modules with the release version
3. **Generates** changelog from conventional commits
4. **Creates** a GitHub Release with all JAR artifacts and SHA256 checksums
5. **Deploys** artifacts to GitHub Packages
6. **Updates** CHANGELOG.md and commits to main

### 4. Verify Release

After the workflow completes (~5-10 minutes):

- Check [GitHub Releases](https://github.com/pacphi/spring-ai-resos/releases)
- Verify artifacts are attached (6 JARs + 6 SHA256 checksums)
- Check [GitHub Packages](https://github.com/pacphi/spring-ai-resos/packages)
- Review the updated [CHANGELOG.md](../CHANGELOG.md)

## Module Artifacts

Each release publishes the following JARs:

| Module     | Artifact ID                  | Description                   |
| ---------- | ---------------------------- | ----------------------------- |
| client     | spring-ai-resos-client       | OpenAPI-generated REST client |
| codegen    | spring-ai-resos-codegen      | Code generation utilities     |
| entities   | spring-ai-resos-entities     | Domain entities (JDBC)        |
| mcp-server | spring-ai-resos-mcp-server   | MCP server implementation     |
| backend    | spring-ai-resos-backend      | Backend API server            |
| mcp-client | spring-ai-resos-mcp-frontend | Chatbot UI with MCP client    |

## Consuming Releases

### From GitHub Packages

Add to your `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

Your GitHub token needs the `read:packages` scope.

Add repository to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/pacphi/spring-ai-resos</url>
  </repository>
</repositories>

<dependency>
  <groupId>me.pacphi</groupId>
  <artifactId>spring-ai-resos-client</artifactId>
  <version>2026.01.07</version>
</dependency>
```

### From GitHub Releases

Download JAR files directly:

```bash
VERSION=2026.01.07

# Download all JARs
gh release download "$VERSION" --repo pacphi/spring-ai-resos --pattern "*.jar"

# Verify checksums
for jar in *.jar; do
  sha256sum -c "$jar.sha256"
done
```

Or download from the [Releases page](https://github.com/pacphi/spring-ai-resos/releases).

## Conventional Commits

All commits must follow the [Conventional Commits](https://www.conventionalcommits.org/) specification for proper changelog generation:

```text
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Commit Types

| Type       | Description             | Changelog Section           |
| ---------- | ----------------------- | --------------------------- |
| `feat`     | New feature             | :sparkles: Features         |
| `fix`      | Bug fix                 | :bug: Bug Fixes             |
| `docs`     | Documentation only      | :memo: Documentation        |
| `style`    | Code style (formatting) | -                           |
| `refactor` | Code refactoring        | :recycle: Code Refactoring  |
| `perf`     | Performance improvement | :zap: Performance           |
| `test`     | Adding/updating tests   | :white_check_mark: Tests    |
| `build`    | Build system changes    | :package: Build System      |
| `ci`       | CI configuration        | :construction_worker: CI/CD |
| `chore`    | Maintenance tasks       | :wrench: Maintenance        |
| `revert`   | Reverts a commit        | -                           |

### Examples

```bash
# Feature
git commit -m "feat: add user authentication endpoint"

# Bug fix with scope
git commit -m "fix(backend): resolve null pointer exception in ReservationService"

# Documentation
git commit -m "docs: update README with new examples"

# Breaking change
git commit -m "feat!: remove deprecated API endpoints

BREAKING CHANGE: The /v1/old endpoint has been removed. Use /v2/new instead."
```

### Local Commit Validation

Install the git hooks for local commit message validation:

```bash
./scripts/install-hooks.sh
```

This installs a `commit-msg` hook that validates conventional commit format.

## Troubleshooting

### Release Workflow Failed

If the release workflow fails:

1. Check the workflow logs in [GitHub Actions](https://github.com/pacphi/spring-ai-resos/actions)
2. An issue will be automatically created with failure details
3. Fix the issue in a new commit to main
4. Delete the failed tag and re-create:

```bash
# Delete remote tag
git push --delete origin 2026.01.07

# Delete local tag
git tag -d 2026.01.07

# Re-create and push
git tag -a "2026.01.07" -m "Release 2026.01.07"
git push origin "2026.01.07"
```

### Version Already Exists

If a tag for today already exists:

```bash
# Use patch version
VERSION=2026.01.07.1  # or .2, .3, etc.
git tag -a "$VERSION" -m "Release $VERSION"
git push origin "$VERSION"
```

### Rollback a Release

To completely remove a release:

```bash
VERSION=2026.01.07

# Delete the GitHub Release (keeps the tag)
gh release delete "$VERSION" --yes

# Or delete both release and tag
gh release delete "$VERSION" --yes
git push --delete origin "$VERSION"
git tag -d "$VERSION"
```

Note: Artifacts in GitHub Packages cannot be deleted once published.

## Release Checklist

Before releasing:

- [ ] All CI checks pass on main
- [ ] Version doesn't already exist as a tag
- [ ] Recent commits follow conventional commit format
- [ ] No uncommitted changes in working directory

After releasing:

- [ ] GitHub Release created with all artifacts
- [ ] CHANGELOG.md updated with new entry
- [ ] Artifacts visible in GitHub Packages
- [ ] Release notes accurately reflect changes
