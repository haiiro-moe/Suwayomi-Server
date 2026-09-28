#!/usr/bin/env bash
#
# Pack the repository for deployment on a server.
#
# Creates a compressed archive containing everything needed to build and run
# Suwairo via Docker Compose (including the WebUI submodule), leaving out
# development artifacts, build outputs, and local-only files.
#
# Usage:
#   ./pack-deploy.sh [output-directory]
#
# The archive is written to the output directory (default: ./dist) as
#   suwairo-deploy-<short-sha>.tar.gz
#
# On the server:
#   tar -xzf suwairo-deploy-<sha>.tar.gz
#   cd suwairo-deploy-<sha>
#   # for SSO: edit compose.sso.yml, then
#   docker compose -f compose.sso.yml up -d --build
#   # or plain:
#   docker compose up -d --build

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:-$REPO_ROOT/dist}"

cd "$REPO_ROOT"

SHORT_SHA="$(git rev-parse --short HEAD)"
ARCHIVE_NAME="suwairo-deploy-$SHORT_SHA"
ARCHIVE_PATH="$OUTPUT_DIR/$ARCHIVE_NAME.tar.gz"

mkdir -p "$OUTPUT_DIR"

# git archive exports all tracked files but not submodules or untracked files,
# so WebUI (a submodule) is added separately below.
STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT

STAGED_ROOT="$STAGING/$ARCHIVE_NAME"
mkdir -p "$STAGED_ROOT"

echo "Exporting tracked files at $SHORT_SHA..."
git archive --format=tar HEAD | tar -x -C "$STAGED_ROOT"

echo "Exporting WebUI submodule (pinned commit)..."
git -C WebUI archive --format=tar HEAD | tar -x -C "$STAGED_ROOT/WebUI"

# Optional, untracked deployment assets: include branding if present
if [ -d "$REPO_ROOT/branding" ] && [ -n "$(ls -A "$REPO_ROOT/branding" 2>/dev/null)" ]; then
    echo "Including branding assets..."
    cp -R "$REPO_ROOT/branding" "$STAGED_ROOT/branding"
fi

# Server setup helper: short deploy instructions
cat > "$STAGED_ROOT/DEPLOY.md" <<'EOF'
# Deploying Suwairo

## Requirements
- Docker + Docker Compose v2
- (SSO) an OIDC provider such as Tinyauth backed by LLDAP

## Plain deployment
    docker compose up -d --build

The server listens on port 4567. First run: create the owner account in the UI.

## SSO deployment
1. Edit `compose.sso.yml` and set SUWAIRO_SSO_ISSUER_URL, SUWAIRO_SSO_CLIENT_ID,
   SUWAIRO_SSO_CLIENT_SECRET, and SUWAIRO_SSO_DEFAULT_ROLE.
2. Register this redirect URI with your OIDC provider:
   https://<server-address>/sso/callback
3. Start:
       docker compose -f compose.sso.yml up -d --build

## Notes
- Data is persisted in the `suwairo-data` volume.
- Branding assets (if included) are mounted read-only from ./branding.
- The WebUI submodule is pinned; `git submodule update --init` is NOT required
  because the archive contains the WebUI sources directly.
EOF

echo "Creating archive..."
tar -czf "$ARCHIVE_PATH" -C "$STAGING" "$ARCHIVE_NAME"

SIZE="$(du -h "$ARCHIVE_PATH" | cut -f1)"
echo
echo "Created: $ARCHIVE_PATH ($SIZE)"
echo
echo "Contents (top level):"
tar -tzf "$ARCHIVE_PATH" | awk -F/ '{print $2}' | sort -u | sed 's/^/  /'
