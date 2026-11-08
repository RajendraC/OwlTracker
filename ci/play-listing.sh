#!/usr/bin/env bash
# Puts the store listing on Google Play with the Play Developer API (v3 "edits"): the app name, short and
# full descriptions from STORE_DIR/listing.txt, and the icon, feature graphic and phone screenshots.
#   play-listing.sh PACKAGE STORE_DIR
# PLAY_TOKEN: a short-lived OAuth access token for the androidpublisher scope. Nothing is stored.
# CONTACT_EMAIL / CONTACT_WEBSITE (optional): the public contact details shown on the listing.
# The service account needs Play Console's "Manage store presence" permission for the app.
set -euo pipefail
export LC_ALL=C.UTF-8 # count characters, not bytes (₹, •)

PKG="$1"
DIR="$2"
: "${PLAY_TOKEN:?PLAY_TOKEN is not set}"
API="${PLAY_API:-https://androidpublisher.googleapis.com}"
APP="$API/androidpublisher/v3/applications/$PKG"
UPLOAD="$API/upload/androidpublisher/v3/applications/$PKG"
TEXT="$DIR/listing.txt"

# --- The text: a heading line, then the value (the full description runs to the end of the file)
[ -s "$TEXT" ] || { echo "::error::No listing text at $TEXT"; exit 1; }
title=$(awk 'f { print; exit } /^APP NAME/ { f = 1 }' "$TEXT")
short=$(awk 'f { print; exit } /^SHORT DESCRIPTION/ { f = 1 }' "$TEXT")
full=$(awk 'f { print } /^FULL DESCRIPTION/ { f = 1 }' "$TEXT" | sed -e '/./,$!d')
check() { # name value limit
  [ -n "$2" ] || { echo "::error::$1 is empty in $TEXT"; exit 1; }
  [ "${#2}" -le "$3" ] || { echo "::error::$1 is ${#2} characters; Google Play allows $3"; exit 1; }
}
check "The app name" "$title" 30
check "The short description" "$short" 80
check "The full description" "$full" 4000

# --- The pictures
icon="$DIR/play-icon-512.png"
feature="$DIR/play-feature-graphic-1024x500.png"
shopt -s nullglob
shots=("$DIR"/phone/*.png)
[ -s "$icon" ] || { echo "::error::No icon at $icon"; exit 1; }
[ -s "$feature" ] || { echo "::error::No feature graphic at $feature"; exit 1; }
[ "${#shots[@]}" -ge 2 ] && [ "${#shots[@]}" -le 8 ] || { echo "::error::Google Play needs 2 to 8 phone screenshots; $DIR/phone has ${#shots[@]}"; exit 1; }

ERR="$(mktemp)"
edit=""
cleanup() {
  # An edit that wasn't saved is thrown away, so it can't block the next release.
  if [ -n "$edit" ]; then curl -sS -o /dev/null -X DELETE -H "Authorization: Bearer $PLAY_TOKEN" "$APP/edits/$edit" || true; fi
  rm -f "$ERR"
}
trap cleanup EXIT

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
  if grep -qiE "permission|HTTP 403" "$ERR"; then
    echo "::error::$1: the Play publishing account isn't allowed to edit the store listing yet. In Play Console → Users and permissions → the play-publisher account → App permissions → Owl Tracker, tick \"Manage store presence\", save, then run this again. ($(tr '\n' ' ' < "$ERR"))"
  else
    echo "::error::$1: $(tr '\n' ' ' < "$ERR")"
  fi
  exit 1
}

edit=$(call POST "$APP/edits" -H "Content-Type: application/json" -d '{}' | jq -r '.id') || fail "Couldn't start a Google Play edit"
[ -n "$edit" ] && [ "$edit" != null ] || { edit=""; echo "::error::Google Play returned no edit id"; exit 1; }

# The listing goes in the app's default language (set when the app was created in Play Console).
lang=$(call GET "$APP/edits/$edit/details" | jq -r '.defaultLanguage // empty') || fail "Couldn't read the app's details"
[ -n "$lang" ] || lang="en-US"

if [ -n "${CONTACT_EMAIL:-}" ]; then
  details=$(jq -n --arg e "$CONTACT_EMAIL" --arg w "${CONTACT_WEBSITE:-}" '{contactEmail: $e} + (if $w == "" then {} else {contactWebsite: $w} end)')
  call PATCH "$APP/edits/$edit/details" -H "Content-Type: application/json" -d "$details" > /dev/null ||
    fail "Google Play didn't accept the contact details"
fi

# PATCH, not PUT: anything else on the listing (such as a promo video added in Play Console) stays.
listing=$(jq -n --arg l "$lang" --arg t "$title" --arg s "$short" --arg f "$full" \
  '{language: $l, title: $t, shortDescription: $s, fullDescription: $f}')
call PATCH "$APP/edits/$edit/listings/$lang" -H "Content-Type: application/json" -d "$listing" > /dev/null ||
  fail "Google Play didn't accept the listing text"

# Pictures: replace each kind as a whole, in file-name order.
put_images() { # type file...
  local type="$1"
  shift
  call DELETE "$APP/edits/$edit/listings/$lang/$type" > /dev/null || fail "Couldn't clear the old $type"
  local f
  for f in "$@"; do
    call POST "$UPLOAD/edits/$edit/listings/$lang/$type?uploadType=media" -H "Content-Type: image/png" --data-binary "@$f" > /dev/null ||
      fail "Google Play didn't accept $(basename "$f") as $type"
  done
}
put_images icon "$icon"
put_images featureGraphic "$feature"
put_images phoneScreenshots "${shots[@]}"

review=sent
if ! call POST "$APP/edits/$edit:commit" > /dev/null; then
  if grep -qi "changesNotSentForReview" "$ERR"; then
    review=waiting
    call POST "$APP/edits/$edit:commit?changesNotSentForReview=true" > /dev/null || fail "Couldn't save the store listing"
  else
    fail "Couldn't save the store listing"
  fi
fi
edit=""

echo "### Store listing sent to Google Play"
echo ""
echo "- Language **$lang**: **$title**, short description (${#short}/80), full description (${#full}/4000)."
echo "- Icon, feature graphic and ${#shots[@]} phone screenshots."
if [ -n "${CONTACT_EMAIL:-}" ]; then echo "- Contact email: $CONTACT_EMAIL"; fi
if [ "$review" = waiting ]; then
  echo "- Other changes were waiting, so it isn't sent yet: open Play Console → **Publishing overview** → **Send changes for review**."
fi
