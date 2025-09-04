#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
# Treat unset variables as an error when substituting.
# Exit with a non-zero status if any command in a pipeline fails.
set -euo pipefail

# --- Configuration ---
# Define the main/parent GitHub repository
: "${MAIN_REPO_OWNER:=shuwariafrica}"
: "${MAIN_REPO_NAME:=sbt-js}"

# Define colors for output for better readability
readonly C_RESET='\033[0m'
readonly C_RED='\033[0;31m'
readonly C_GREEN='\033[0;32m'
readonly C_YELLOW='\033[0;33m'

# --- Helper Functions ---

# Print an error message and exit.
# Usage: error "Your error message"
error() {
  echo -e "${C_RED}Error: $1${C_RESET}" >&2
  exit 1
}

# Print a success message.
# Usage: success "Your success message"
success() {
  echo -e "${C_GREEN}Success: $1${C_RESET}"
}

# Print an informational message.
# Usage: info "Your info message"
info() {
  echo -e "${C_YELLOW}$1${C_RESET}"
}

# Function to display help message
show_help() {
  echo "Usage: $(basename "$0") <version>"
  echo
  echo "Creates and pushes a signed git release tag for the specified version."
  echo "The version must follow semantic versioning with specific pre-release classifiers."
  echo "This script ensures the working directory is clean and up-to-date with its upstream on the main repository before proceeding."
  echo
  echo "Main repository settings:"
  echo "  MAIN_REPO_OWNER=${MAIN_REPO_OWNER}"
  echo "  MAIN_REPO_NAME=${MAIN_REPO_NAME}"
  echo
  echo "Allowed pre-release classifiers (case-sensitive):"
  echo "  - milestone.N (or m.N)"
  echo "  - alpha.N"
  echo "  - beta.N"
  echo "  - rc.N"
  echo "  - SNAPSHOT"
  echo
  echo "Examples:"
  echo "  - 1.2.3"
  echo "  - 1.2.3-alpha.1"
  echo "  - 1.2.3-SNAPSHOT"
  echo
  echo "Options:"
  echo "  -h, --help    Show this help message and exit"
}

# --- Main Script Logic ---

# Check for help flag
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

# Check if a version parameter is provided
if [ -z "${1:-}" ]; then
  error "Version parameter is required."
  show_help
  exit 1
fi

# Regex for semver core + specific pre-release classifiers.
# 1. Core version: MAJOR.MINOR.PATCH
# 2. Pre-release (optional): A hyphen followed by EITHER:
#    a. (milestone|m|alpha|beta|rc) followed by a dot and a number (e.g., -rc.1)
#    b. The literal string "SNAPSHOT"
# 3. Build metadata (optional): A plus sign followed by identifiers.
readonly SEMVER_REGEX='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-((milestone|m|alpha|beta|rc)\.([1-9][0-9]*)|SNAPSHOT))?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$'
readonly VERSION="$1"

# Validate the version parameter against the spec
if ! [[ "$VERSION" =~ $SEMVER_REGEX ]]; then
  error "Version '$VERSION' is not valid. See --help for allowed formats."
fi

# Check if the working tree is clean
if [ -n "$(git status --porcelain)" ]; then
  info "There are uncommitted changes in the working directory:"
  git status
  error "Please commit or stash your changes before creating a release tag."
fi

info "Working directory is clean. Proceeding with tagging."

# Compute the tag name
readonly TAG_NAME="v$VERSION"

# Locate the remote that points to the main repository (HTTPS or SSH)
MAIN_REPO_HTTPS="https://github.com/${MAIN_REPO_OWNER}/${MAIN_REPO_NAME}"
MAIN_REPO_HTTPS_GIT="${MAIN_REPO_HTTPS}.git"
MAIN_REPO_SSH="git@github.com:${MAIN_REPO_OWNER}/${MAIN_REPO_NAME}.git"
MAIN_REMOTE=""
for r in $(git remote); do
  urls=$(git remote get-url --all "$r" 2>/dev/null || true)
  if echo "$urls" | grep -qxF "$MAIN_REPO_HTTPS" || \
     echo "$urls" | grep -qxF "$MAIN_REPO_HTTPS_GIT" || \
     echo "$urls" | grep -qxF "$MAIN_REPO_SSH"; then
    MAIN_REMOTE="$r"
    break
  fi
done

if [ -z "$MAIN_REMOTE" ]; then
  error "No git remote points to ${MAIN_REPO_OWNER}/${MAIN_REPO_NAME}. Add one (e.g., 'upstream') and try again."
fi

# Ensure we are on a branch with an upstream and it's up-to-date
current_branch=$(git rev-parse --abbrev-ref HEAD)
if [[ "$current_branch" == "HEAD" ]]; then
  error "Detached HEAD is not supported. Check out a branch with an upstream tracking branch."
fi

if ! upstream_ref=$(git rev-parse --abbrev-ref --symbolic-full-name @{u} 2>/dev/null); then
  error "Current branch '$current_branch' has no upstream. Set an upstream (e.g., 'git push -u origin $current_branch') before releasing."
fi

# Ensure the upstream remote is the main repository remote
upstream_remote=${upstream_ref%%/*}
if [[ "$upstream_remote" != "$MAIN_REMOTE" ]]; then
  error "Upstream for '$current_branch' is '$upstream_remote', but must be '$MAIN_REMOTE' (pointing to ${MAIN_REPO_OWNER}/${MAIN_REPO_NAME}). Set upstream accordingly."
fi

# Fetch latest commits and tags from the main repository remote
info "Fetching from '$MAIN_REMOTE' (main repository) and its tags..."
git fetch --prune "$MAIN_REMOTE" >/dev/null 2>&1 || true
git fetch --tags --prune "$MAIN_REMOTE" >/dev/null 2>&1 || true

# Check ahead/behind relative to upstream
ahead=0; behind=0
IFS=$'\t ' read -r ahead behind <<< "$(git rev-list --left-right --count HEAD...@{u})"
if (( behind > 0 )); then
  error "Your branch '$current_branch' is behind its upstream by $behind commit(s). Pull/rebase before releasing."
fi
if (( ahead > 0 )); then
  error "Your branch '$current_branch' has $ahead unpushed commit(s). Push before releasing."
fi

# Check if the tag already exists locally (strictly as a tag)
if git show-ref --tags --verify --quiet "refs/tags/$TAG_NAME"; then
  error "Tag '$TAG_NAME' already exists locally."
fi

# Check if the tag already exists on the main repository remote
if [ -n "$(git ls-remote --tags "$MAIN_REMOTE" "refs/tags/$TAG_NAME")" ]; then
  error "Tag '$TAG_NAME' already exists on remote '$MAIN_REMOTE' (main repository)."
fi

info "Creating annotated and GPG-signed tag: $TAG_NAME"

# Create an annotated and GPG-signed tag
# The --sign flag will fail if a GPG key is not configured, which is desired behavior.
git tag --sign --annotate "$TAG_NAME" -m "Release version $TAG_NAME"

success "Tagged release $TAG_NAME locally."

info "Pushing tag to main repository remote '$MAIN_REMOTE'..."

# Push the tag to the main repository remote
git push "$MAIN_REMOTE" "$TAG_NAME"

success "Pushed tag '$TAG_NAME' to the remote repository."
