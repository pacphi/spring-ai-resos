#!/bin/bash
# Install git hooks for conventional commit enforcement
#
# Usage: ./scripts/install-hooks.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
HOOK_DIR="$PROJECT_ROOT/.git/hooks"
COMMIT_MSG_HOOK="$HOOK_DIR/commit-msg"

echo "Installing git hooks..."

# Create hooks directory if it doesn't exist
mkdir -p "$HOOK_DIR"

# Create commit-msg hook
cat > "$COMMIT_MSG_HOOK" << 'EOF'
#!/bin/bash
# Conventional Commits validation hook
#
# This hook validates that commit messages follow the Conventional Commits format.
# See: https://www.conventionalcommits.org/

commit_msg_file=$1
commit_msg=$(cat "$commit_msg_file")

# Conventional commit pattern
# Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert
pattern="^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\(.+\))?(!)?: .{1,}"

# Also allow merge commits and Dependabot format
merge_pattern="^Merge "
bump_pattern="^Bump "

if [[ $commit_msg =~ $pattern ]] || [[ $commit_msg =~ $merge_pattern ]] || [[ $commit_msg =~ $bump_pattern ]]; then
    exit 0
else
    echo ""
    echo "ERROR: Commit message does not follow Conventional Commits format."
    echo ""
    echo "Expected format: <type>[optional scope]: <description>"
    echo ""
    echo "Types:"
    echo "  feat     - A new feature"
    echo "  fix      - A bug fix"
    echo "  docs     - Documentation only changes"
    echo "  style    - Changes that do not affect the meaning of the code"
    echo "  refactor - A code change that neither fixes a bug nor adds a feature"
    echo "  perf     - A code change that improves performance"
    echo "  test     - Adding missing tests or correcting existing tests"
    echo "  build    - Changes that affect the build system or external dependencies"
    echo "  ci       - Changes to CI configuration files and scripts"
    echo "  chore    - Other changes that don't modify src or test files"
    echo "  revert   - Reverts a previous commit"
    echo ""
    echo "Examples:"
    echo "  feat: add user authentication"
    echo "  fix(backend): resolve null pointer exception"
    echo "  docs: update README with new examples"
    echo "  feat!: remove deprecated API (breaking change)"
    echo ""
    echo "Your message: $commit_msg"
    echo ""
    exit 1
fi
EOF

chmod +x "$COMMIT_MSG_HOOK"

echo "Git hooks installed successfully!"
echo ""
echo "The commit-msg hook will now validate that your commit messages"
echo "follow the Conventional Commits format."
