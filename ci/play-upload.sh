#!/usr/bin/env bash
# Sends a signed .aab to a Google Play testing track with the Play Developer API (v3 "edits").
#   play-upload.sh PACKAGE FILE.aab TRACK VERSION_NAME
# TRACK: internal (Internal testing) or alpha (Closed testing). Production is left to Play Console on
# purpose: a person clicks "Promote release" there.
# PLAY_TOKEN: a short-lived OAuth access token for the androidpublisher scope (from
# google-github-actions/auth in the release workflow). Nothing is stored.
set -euo pipefail

PKG="$1"
AAB="$2"
TRACK="$3"
NAME="$4"
: "${PLAY_TOKEN:?PLAY_TOKEN is not set}"
API="${PLAY_API:-https://androidpublisher.googleapis.com}"
APP="$API/androidpublisher/v3/applications/$PKG"
UPLOAD="$API/upload/androidpublisher/v3/applications/$PKG"

case "$TRACK" in
  internal | alpha) ;;
  *) echo "::error::Track must be internal or alpha, not '$TRACK'"; exit 1 ;;
esac
[ -s "$AAB" ] || { echo "::error::No bundle at $AAB"; exit 1; }

ERR="$(mktemp)"
trap 'rm -f "$ERR"' EXIT

# call METHOD URL [curl options...]: prints the response; on an HTTP error saves Google's message in $ERR.
call() {
  local method="$1" url="$2"
  shift 2
  local body code
  body="$(mktemp)"
  code=$(curl -sS -o "$body" -w '%{http_code}' -X "$method" -H "Authorization: Bearer $PLAY_TOKEN" "$@" "$url") || {
    echo "could not reach Google Play" > "$ERR"
    rm -f "$body"
    return 1
  }
  if [ "$code" -ge 300 ]; then
    { jq -r '.error.message // empty' "$body" 2>/dev/null || true; } > "$ERR"
    [ -s "$ERR" ] || head -c 400 "$body" > "$ERR"
    echo " (HTTP $code)" >> "$ERR"
    rm -f "$body"
    return 1
  fi
  cat "$body"
  rm -f "$body"
}

fail() {
  echo "::error::$1: $(tr '\n' ' ' < "$ERR")"
  exit 1
}

edit=$(call POST "$APP/edits" -H "Content-Type: application/json" -d '{}' | jq -r '.id') || fail "Couldn't start a Google Play edit"
[ -n "$edit" ] && [ "$edit" != null ] || { echo "::error::Google Play returned no edit id"; exit 1; }

code=$(call POST "$UPLOAD/edits/$edit/bundles?uploadType=media" -H "Content-Type: application/octet-stream" --data-binary "@$AAB" | jq -r '.versionCode') ||
  fail "Google Play didn't accept the bundle"

release() {
  jq -n --arg t "$TRACK" --arg n "$NAME" --arg c "$code" --arg s "$1" \
    '{track: $t, releases: [{name: $n, versionCodes: [$c], status: $s}]}'
}

# Testers get it straight away ("completed"). An app Google hasn't published anywhere yet only
# accepts drafts; then the release waits in Play Console for someone to roll it out.
status=completed
if ! call PUT "$APP/edits/$edit/tracks/$TRACK" -H "Content-Type: application/json" -d "$(release completed)" > /dev/null; then
  if grep -qi "draft" "$ERR"; then
    status=draft
    call PUT "$APP/edits/$edit/tracks/$TRACK" -H "Content-Type: application/json" -d "$(release draft)" > /dev/null ||
      fail "Google Play didn't accept the release"
  else
    fail "Google Play didn't accept the release"
  fi
fi

# Normally the change goes to Google straight away. If other changes are waiting in Play Console,
# Google asks for them to be sent together, by hand, from Publishing overview.
review=sent
if ! call POST "$APP/edits/$edit:commit" > /dev/null; then
  if grep -qi "changesNotSentForReview" "$ERR"; then
    review=waiting
    call POST "$APP/edits/$edit:commit?changesNotSentForReview=true" > /dev/null || fail "Couldn't save the release"
  else
    fail "Couldn't save the release"
  fi
fi

track_name=$([ "$TRACK" = internal ] && echo "Internal testing" || echo "Closed testing")
echo "### Sent to Google Play"
echo ""
echo "- Version **$NAME** (code $code) is on **$track_name**."
if [ "$status" = draft ]; then
  echo "- It's a **draft**: open Play Console → Test and release → $track_name and roll it out."
elif [ "$review" = waiting ]; then
  echo "- Other changes were waiting, so it isn't sent yet: open Play Console → **Publishing overview** → **Send changes for review**."
else
  echo "- Testers who already joined get it as an update, usually within an hour."
fi
