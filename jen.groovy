BASE="https://dhcsprodcognos.ca.analytics.ibm.com/api/v1"

# (You already have session.json from Step 1)
SESSION_KEY=$(python3 - <<'PY'
import json; print(json.load(open("session.json")).get("session_key",""))
PY
)

# Get XSRF-TOKEN cookie
curl -sS --fail-with-body "$BASE/session" -c cookies.txt -b cookies.txt -o /dev/null
XSRF=$(awk '$6=="XSRF-TOKEN"{print $7}' cookies.txt | tail -n1)

# Build the IBM-BA-Authorization header correctly
if [[ "$SESSION_KEY" == CAM\ * ]]; then
  AUTH_VALUE="$SESSION_KEY"
else
  AUTH_VALUE="CAM $SESSION_KEY"
fi

# Sanity call (should be 200 and return JSON)
curl -sS --fail-with-body "$BASE/extensions" \
  -H "IBM-BA-Authorization: $AUTH_VALUE" \
  ${XSRF:+ -H "X-XSRF-TOKEN: $XSRF"} \
  -b cookies.txt -c cookies.txt -o extensions.json

echo "OK — extensions.json saved (session works)."
