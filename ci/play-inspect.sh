#!/usr/bin/env bash
# Read-only look at what Google Play holds for the app: store listing per language (text, video,
# pictures), contact details, testing tracks with their countries and tester groups, and in-app products.
# Opens an edit to read, then throws it away; changes nothing.
#   play-inspect.sh PACKAGE
# PLAY_TOKEN: a short-lived OAuth access token for the androidpublisher scope.
set -euo pipefail
export LC_ALL=C.UTF-8

PKG="$1"
: "${PLAY_TOKEN:?PLAY_TOKEN is not set}"
API="${PLAY_API:-https://androidpublisher.googleapis.com}"
APP="$API/androidpublisher/v3/applications/$PKG"

edit=""
cleanup() {
  if [ -n "$edit" ]; then curl -sS -o /dev/null -X DELETE -H "Authorization: Bearer $PLAY_TOKEN" "$APP/edits/$edit" || true; fi
}
trap cleanup EXIT

# get URL: prints the JSON, or {"error": "..."} so one refused read doesn't hide the rest.
get() {
  local body code
  body="$(mktemp)"
  code=$(curl -sS -o "$body" -w '%{http_code}' -H "Authorization: Bearer $PLAY_TOKEN" "$1") || code=000
  if [ "$code" -ge 300 ] || [ "$code" = 000 ]; then
    jq -n --arg m "$(jq -r '.error.message // empty' "$body" 2>/dev/null || true) (HTTP $code)" '{error: $m}'
  else
    cat "$body"
  fi
  rm -f "$body"
}

edit=$(curl -sS -X POST -H "Authorization: Bearer $PLAY_TOKEN" -H "Content-Type: application/json" -d '{}' "$APP/edits" | jq -r '.id // empty')
[ -n "$edit" ] || { echo "::error::Couldn't open a Google Play edit to read from"; exit 1; }
E="$APP/edits/$edit"

echo "### What Google Play holds for $PKG"
echo ""
details=$(get "$E/details")
echo "**App details:** $(jq -r 'if .error then "can'"'"'t read: " + .error else "default language \(.defaultLanguage // "?"), contact email \(.contactEmail // "none"), website \(.contactWebsite // "none")" end' <<< "$details")"
lang=$(jq -r '.defaultLanguage // empty' <<< "$details")
echo ""

listings=$(get "$E/listings")
if jq -e '.error' <<< "$listings" > /dev/null; then
  echo "**Store listings:** can't read: $(jq -r .error <<< "$listings")"
else
  n=$(jq '.listings // [] | length' <<< "$listings")
  echo "**Store listings saved: $n**"
  echo ""
  jq -r '.listings // [] | .[] | "- \(.language): title \"\(.title // "")\", short description \((.shortDescription // "") | length) chars, full description \((.fullDescription // "") | length) chars, video \(if (.video // "") == "" then "none" else .video end)"' <<< "$listings"
  langs=$(jq -r '.listings // [] | .[].language' <<< "$listings")
  [ -n "$lang" ] && ! grep -qx "$lang" <<< "$langs" && echo "- **No listing in the default language ($lang)**: Google Play shows the default language's listing, so it falls back to the package name."
  echo ""
  for l in $langs; do
    line="Pictures ($l):"
    for t in icon featureGraphic phoneScreenshots sevenInchScreenshots tenInchScreenshots; do
      c=$(get "$E/listings/$l/$t" | jq -r 'if .error then "?" else (.images // [] | length) end')
      line="$line $t $c,"
    done
    echo "- ${line%,}"
  done
fi
echo ""

tracks=$(get "$E/tracks")
if jq -e '.error' <<< "$tracks" > /dev/null; then
  echo "**Tracks:** can't read: $(jq -r .error <<< "$tracks")"
else
  echo "**Tracks:**"
  echo ""
  jq -r '.tracks // [] | .[] | "- \(.track): " + ([.releases // [] | .[] | "\(.name // "?") (codes \((.versionCodes // []) | join(","))) \(.status)"] | join("; ") | if . == "" then "no releases" else . end)' <<< "$tracks"
fi
echo ""

# Where each testing track is offered, and who tests it
for t in internal alpha; do
  ca=$(get "$E/countryAvailability/$t")
  echo "**Countries ($t):** $(jq -r 'if .error then "can'"'"'t read: " + .error else (if .syncWithProduction then "same as production; " else "" end) + ((.countries // []) | map(.countryCode) | if length == 0 then "none listed" elif length > 12 then "\(length) countries" + (if index("IN") then ", India included" else ", India NOT included" end) else join(", ") end) + (if .restOfWorld then " + rest of world" else "" end) end' <<< "$ca")"
  te=$(get "$E/testers/$t")
  echo "**Tester Google Groups ($t):** $(jq -r 'if .error then "can'"'"'t read: " + .error else ((.googleGroups // []) | if length == 0 then "none (email lists don'"'"'t show through the API)" else join(", ") end) end' <<< "$te")"
done
echo ""

# In-app products: the ₹50 unlock should be here, active, priced for India
otp=$(get "$APP/oneTimeProducts")
if jq -e '.error' <<< "$otp" > /dev/null; then
  echo "**One-time products:** can't read: $(jq -r .error <<< "$otp")"
else
  n=$(jq '.oneTimeProducts // [] | length' <<< "$otp")
  echo "**One-time products: $n**"
  jq -r '.oneTimeProducts // [] | .[] | "- \(.productId): " + ([.listings // [] | .[] | "\(.languageCode) \"\(.title)\""] | join(", ")) + "; options: " + ([.purchaseOptions // [] | .[] | "\(.purchaseOptionId) \(.state // "?")" + ([.regionalPricingAndAvailabilityConfigs // [] | .[] | select(.regionCode == "IN") | " India \(.price.currencyCode) \(.price.units // "0").\((.price.nanos // 0) / 10000000 | floor) \(.availability // "")"] | join(""))] | join("; "))' <<< "$otp"
fi
iap=$(get "$APP/inappproducts")
if jq -e '.error' <<< "$iap" > /dev/null; then
  echo "**In-app products (older API):** can't read: $(jq -r .error <<< "$iap")"
else
  echo "**In-app products (older API): $(jq '.inappproduct // [] | length' <<< "$iap")**"
  jq -r '.inappproduct // [] | .[] | "- \(.sku): \(.status), \(.purchaseType), default \((.defaultPrice.priceMicros // "0") | tonumber / 1000000) \(.defaultPrice.currency // ""), India \(((.prices.IN.priceMicros // "0") | tonumber) / 1000000) \(.prices.IN.currency // "")"' <<< "$iap"
fi
