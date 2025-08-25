#!/usr/bin/env python3
import json
import requests
import os
import sys
from requests.packages.urllib3.exceptions import InsecureRequestWarning

DEBUG = os.environ.get("DEBUG") == "1"

# Disable SSL warnings since we're using -k (insecure) flag equivalent
requests.packages.urllib3.disable_warnings(InsecureRequestWarning)


def mask_sensitive_data(data):
    """Mask sensitive data for logging purposes"""
    if isinstance(data, list):
        return [mask_sensitive_data(item) for item in data]
    elif isinstance(data, dict):
        masked = {}
        for key, value in data.items():
            if key.lower() in ['apikey', 'password', 'token', 'secret', 'key']:
                masked[key] = '*' * 8  # Mask sensitive fields
            else:
                masked[key] = value
        return masked
    return data


def login_init(credentials):
    """Make the login API request and return the x-auth-token"""
    url = "https://cgrptmcip01.cloud.cammis.ca.gov/api/login"
    headers = {'Content-Type': 'application/json'}

    # Parse the credentials string to get the actual data
    try:
        if isinstance(credentials, str):
            payload = json.loads(credentials)
        else:
            payload = credentials

        # Ensure payload is a list
        if not isinstance(payload, list):
            payload = [payload]

    except json.JSONDecodeError as e:
        print(f"Error parsing credentials: {e}", file=sys.stderr)
        return None

    # Optional debug (stderr only)
    if DEBUG:
        print("== DEBUG: Sending masked credentials ==", file=sys.stderr)
        masked_payload = mask_sensitive_data(payload)
        print(json.dumps(masked_payload, indent=2), file=sys.stderr)

    try:
        response = requests.post(
            url,
            headers=headers,
            json=payload,
            verify=False,
            allow_redirects=True
        )

        # Success: return the token, no stdout noise
        if response.status_code == 200:
            auth_token = response.headers.get('x-auth-token')
            if auth_token:
                return auth_token
            else:
                if DEBUG:
                    print("No x-auth-token found in response headers", file=sys.stderr)
                return None

        # Non-200: optional debug to stderr
        if DEBUG:
            print(f"Login failed. Status Code: {response.status_code}", file=sys.stderr)
        return None

    except requests.exceptions.RequestException as e:
        print(f"Error making request: {e}", file=sys.stderr)
        return None
