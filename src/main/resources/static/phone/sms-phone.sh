#!/data/data/com.termux/files/usr/bin/bash
# Turns an Android phone into a sender for the SMS gateway: takes waiting messages, texts them, reports back.
#   SERVER=https://<gateway> PHONE_KEY=<key from the Phones page> bash sms-phone.sh
# Runs in Termux with Termux:API (both from F-Droid): pkg install termux-api curl jq; allow Termux:API to send SMS.
set -uo pipefail

SERVER="${SERVER:?set SERVER to the gateway address, e.g. https://sms.example.com}"
KEY="${PHONE_KEY:?set PHONE_KEY to the key from the Phones page}"
SIM_SLOT="${SIM_SLOT:-}"
EVERY="${EVERY:-5}"

post() {
  curl -sS --max-time 20 -X POST "${SERVER%/}/api/v1/phone/$1" \
    -H 'Content-Type: application/json' -H "X-Device-Key: $KEY" -d "$2"
}

field() {
  printf '%s' "$1" | jq -r "$2" 2>/dev/null
}

slot=()
[ -n "$SIM_SLOT" ] && slot=(-s "$SIM_SLOT")

termux-wake-lock
echo "Sending for $SERVER, asking every ${EVERY}s (Ctrl+C to stop)"

while true; do
  out="$(post pull '{"limit": 5}')"
  if [ "$(field "$out" '.success')" != true ]; then
    echo "$(date +%T) ✗ pull: $(field "$out" '.error.message' || echo "${out:-no reply}")" >&2
    sleep 30
    continue
  fi

  while read -r msg; do
    [ -n "$msg" ] || continue
    id="$(field "$msg" '.id')"
    to="$(field "$msg" '.to')"
    text="$(field "$msg" '.text')"
    if err="$(termux-sms-send -n "$to" ${slot[@]+"${slot[@]}"} "$text" 2>&1 </dev/null)"; then
      sent=true
      err=""
    else
      sent=false
    fi
    outcome="$(jq -nc --arg i "$id" --argjson s "$sent" --arg e "$err" \
      '{id: $i, sent: $s, error: (if $e == "" then null else ($e | .[0:500]) end)}')"
    post report "$outcome" >/dev/null
    echo "$(date +%T) $([ "$sent" = true ] && echo ✓ || echo ✗) $to"
  done < <(printf '%s' "$out" | jq -c '.data[]?')

  sleep "$EVERY"
done
