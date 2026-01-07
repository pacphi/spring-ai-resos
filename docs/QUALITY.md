# Quality Tooling

This project uses pnpm to manage quality tools for linting, formatting, and validating non-Java assets including markdown, YAML, JSON, and CSV files.

## Prerequisites

- **Node.js** >= 18.0.0
- **pnpm** >= 8.0.0
- **lychee** (for link checking) - [Installation instructions](#installing-lychee)
- **yamllint** (for YAML linting) - typically installed via `pip install yamllint`

## Installation

```bash
# Install quality tools
pnpm install

# Install all dependencies (including frontend)
pnpm install:all
```

## Available Scripts

### Formatting (Prettier)

| Command             | Description                             |
| ------------------- | --------------------------------------- |
| `pnpm format`       | Format all supported files              |
| `pnpm format:check` | Check formatting without making changes |
| `pnpm format:md`    | Format markdown files only              |
| `pnpm format:yaml`  | Format YAML files only                  |
| `pnpm format:json`  | Format JSON files only                  |

### Linting

| Command              | Description                           |
| -------------------- | ------------------------------------- |
| `pnpm lint`          | Run all linters (markdown + YAML)     |
| `pnpm lint:md`       | Lint markdown files with markdownlint |
| `pnpm lint:yaml`     | Lint YAML files with yamllint         |
| `pnpm lint:links`    | Check all links with lychee           |
| `pnpm lint:links:md` | Check links in markdown files only    |

### Data Validation

| Command             | Description                                |
| ------------------- | ------------------------------------------ |
| `pnpm csv:validate` | Validate CSV files in `backend/seed-data/` |

The CSV validator automatically detects delimiters (comma, semicolon, or tab) and checks for:

- Header integrity (empty or duplicate headers)
- Column count consistency across rows
- Basic structural validity

### Frontend Management

Commands for managing the React frontend in `mcp-client/src/main/frontend/`:

| Command                            | Description                                              |
| ---------------------------------- | -------------------------------------------------------- |
| `pnpm frontend:install`            | Install frontend dependencies                            |
| `pnpm frontend:audit`              | Security audit of frontend dependencies                  |
| `pnpm frontend:outdated`           | List outdated frontend dependencies                      |
| `pnpm frontend:update`             | Update dependencies to compatible versions               |
| `pnpm frontend:update:interactive` | Interactive dependency update                            |
| `pnpm frontend:update:latest`      | Update to latest versions (may include breaking changes) |
| `pnpm frontend:build`              | Build the frontend for production                        |
| `pnpm frontend:dev`                | Start frontend development server                        |
| `pnpm frontend:lint`               | Run frontend linter                                      |

### Combined Scripts

| Command              | Description                                      |
| -------------------- | ------------------------------------------------ |
| `pnpm check`         | Run format check + all linters                   |
| `pnpm fix`           | Auto-fix formatting and markdown lint issues     |
| `pnpm quality`       | Full quality check (format + lint + links + CSV) |
| `pnpm precommit`     | Quick pre-commit check (format + markdown lint)  |
| `pnpm deps:audit`    | Audit both root and frontend dependencies        |
| `pnpm deps:outdated` | Check outdated dependencies (root + frontend)    |

## Configuration Files

| File                       | Purpose                      |
| -------------------------- | ---------------------------- |
| `.prettierrc`              | Prettier formatting rules    |
| `.prettierignore`          | Files excluded from Prettier |
| `.markdownlint-cli2.jsonc` | Markdown linting rules       |
| `.yamllint`                | YAML linting configuration   |
| `.lychee.toml`             | Link checker configuration   |

## Installing Lychee

Lychee is a fast link checker written in Rust. Install it using one of these methods:

```bash
# macOS with Homebrew
brew install lychee

# With Cargo (Rust package manager)
cargo install lychee

# Arch Linux
pacman -S lychee

# Alpine Linux
apk add lychee

# Docker
docker run --rm -it lycheeverse/lychee --help
```

For more options, see the [lychee installation guide](https://github.com/lycheeverse/lychee#installation).

## Usage Examples

### Pre-commit Workflow

Before committing changes, run:

```bash
pnpm precommit
```

This performs a quick format check and markdown lint to catch common issues.

### Full Quality Check

For a comprehensive quality check before a release or PR:

```bash
pnpm quality
```

This runs all formatting checks, linters, link validation, and CSV validation.

### Fixing Issues

To automatically fix formatting and markdown issues:

```bash
pnpm fix
```

### Checking Frontend Dependencies

To audit frontend security and check for updates:

```bash
pnpm deps:audit      # Security audit
pnpm deps:outdated   # Check for outdated packages
```

## Customization

### Adding Proper Names to Markdown Lint

Edit `.markdownlint-cli2.jsonc` and add names to the `MD044` rule:

```jsonc
"MD044": {
  "names": ["Spring", "Java", "Maven", "GitHub", "PostgreSQL", "Docker", "YourName"],
  "code_blocks": false,
  "html_elements": false,
}
```

### Excluding URLs from Link Checking

Edit `.lychee.toml` and add patterns to the `exclude` array:

```toml
exclude = [
  "^https?://internal-domain\\.com",
  # ... other patterns
]
```

### Ignoring Files from Formatting

Add patterns to `.prettierignore`:

```text
# Ignore generated files
**/generated/
```
