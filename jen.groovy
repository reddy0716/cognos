BASE="https://dhcsprodcog.ca.analytics.ibm.com/api/v1"
APIKEY="<PASTE_YOUR_API_KEY>"

# Create a session with the API key
curl -sS --fail-with-body -X PUT "$BASE/session" \
  -H "Content-Type: application/json" \
  -d '{"parameters":[{"name":"CAMAPILoginKey","value":"'"$APIKEY"'"}]}' \
  -c cookies.txt -b cookies.txt \
  -D headers.txt -o session.json

# Print the session_key (should be non-empty)
python3 - <<'PY'
import json,sys
print(json.load(open("session.json")).get("session_key",""))
PY
