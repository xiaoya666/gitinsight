#!/usr/bin/env bash
# Publish the plugin to the JetBrains Marketplace without ever pasting the token inline.
#
# Token resolution (first match wins):
#   1. $PUBLISH_TOKEN environment variable
#   2. ~/.gitinsight-publish-token   (chmod 600, lives outside the repo)
#
# One-time setup of the token file:
#   printf '%s' '<YOUR_PERMANENT_TOKEN>' > ~/.gitinsight-publish-token
#   chmod 600 ~/.gitinsight-publish-token
#
# Then just run:  bash scripts/publish.sh
# The token is exported only for the gradle process and is never echoed.
set -euo pipefail
cd "$(dirname "$0")/.."

TOKEN_FILE="${HOME}/.gitinsight-publish-token"

if [ -n "${PUBLISH_TOKEN:-}" ]; then
  : # use the value already in the environment
elif [ -f "$TOKEN_FILE" ]; then
  PUBLISH_TOKEN="$(cat "$TOKEN_FILE")"
else
  echo "No token found." >&2
  echo "  Set \$PUBLISH_TOKEN, or save it to $TOKEN_FILE (chmod 600)." >&2
  echo "  Generate one at https://plugins.jetbrains.com/author/me/tokens" >&2
  exit 1
fi
export PUBLISH_TOKEN

echo "Building and publishing $(grep '^pluginVersion' gradle.properties | tr -d ' ' | cut -d= -f2) ..."
./gradlew clean buildPlugin publishPlugin

echo "Published. Revoke the token when done: https://plugins.jetbrains.com/author/me/tokens"
