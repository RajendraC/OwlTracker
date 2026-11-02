#!/usr/bin/env bash
# Usage: ci/describe-bundle.sh <app-release.aab> <version name> <version code>
# Prints, in Markdown, what a release bundle contains and fails if it isn't properly signed
# with a real upload key (not the debug key).
set -euo pipefail
AAB="$1"; NAME="$2"; CODE="$3"
[ -s "$AAB" ] || { echo "::error::No bundle at $AAB"; exit 1; }
CERT=$(keytool -printcert -jarfile "$AAB" 2>/dev/null || true)
OWNER=$(printf '%s\n' "$CERT" | awk -F': ' '/^Owner:/{print $2; exit}')
SHA256=$(printf '%s\n' "$CERT" | awk '/SHA256:/{print $2; exit}')
[ -n "$SHA256" ] || { echo "::error::The bundle is not signed"; exit 1; }
case "$OWNER" in
  *"Android Debug"*|*"SpendLens Debug"*) echo "::error::The bundle is signed with a debug key, not the upload key"; exit 1 ;;
esac
SIZE=$(du -h "$AAB" | cut -f1)
cat <<MD
### Release bundle ready

| | |
| --- | --- |
| Package | com.owl.tracker |
| Version name | $NAME |
| Version code | $CODE |
| File size | $SIZE |
| Signed by | $OWNER |
| Upload key SHA-256 | \`$SHA256\` |

Check the SHA-256 above matches the fingerprint of your upload key (step 3 of the guide).
MD
