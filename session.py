#!/usr/bin/env python3
import requests
import json
import os
import logging
from requests.packages.urllib3.exceptions import InsecureRequestWarning

# Disable SSL warnings since we're using -k (insecure) flag equivalent
requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

def get_cam_passport_id(cognos_url, credentials=None, credentials_file=None):
    """
    Connect to Cognos and extract camPassportId from the session cookies.
    
    Args:
        cognos_url (str): The URL of the Cognos instance
        credentials (dict, optional): Dictionary or JSON string with login credentials
        credentials_file (str, optional): Path to credentials file
        
    Returns:
        str: The camPassportId value if found, None otherwise
    """
    # Setup logging
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
    
    # Load credentials from file if specified
    if credentials_file and not credentials:
        try:
            if not os.path.exists(credentials_file):
                logging.error(f"Credentials file '{credentials_file}' not found!")
                return None
                
            with open(credentials_file, 'r') as file:
                credentials_data = json.load(file)
                logging.info(f"Successfully loaded credentials from {credentials_file}")
                # Keep as dictionary for Cognos authentication
                credentials = credentials_data
        except Exception as e:
            logging.error(f"Error loading credentials file: {e}")
            return None
    
    # Parse credentials if provided as a string
    if isinstance(credentials, str):
        try:
            credentials = json.loads(credentials)
        except json.JSONDecodeError as e:
            logging.error(f"Error parsing credentials JSON: {e}")
            return None
    
    # Initialize session to maintain cookies
    session = requests.Session()
    
    # Process credentials for Cognos format (extract from array if needed)
    if isinstance(credentials, list) and len(credentials) > 0:
        creds = credentials[0]  # Use first credential in list
    else:
        creds = credentials
    
    try:
        # Log masked credentials for debugging
        logging.info("== DEBUG: Authentication with masked credentials ==")
        masked_creds = mask_sensitive_data(creds)
        logging.info(json.dumps(masked_creds, indent=2))
        
        # First make a GET request to get any cookies/redirect info
        logging.info(f"Making initial connection to Cognos: {cognos_url}")
        
        # Initial GET to handle redirects and collect cookies
        response = session.get(
            cognos_url,
            verify=False,
            allow_redirects=True
        )
        
        # Check for camPassportId in cookies after initial request
        passport_id = check_for_passport_id(session, response)
        if passport_id:
            return passport_id
        
        # If no passport found, try authentication
        if creds:
            logging.info("No passport found in initial request, attempting authentication...")
            
            # Try to find login endpoint
            # Common Cognos login endpoints
            login_endpoints = [
                "/bi/v1/login",
                "/bi/v1/disp",
                "/bi/?b_action=xts.run&m=portal/login.xts",
                "/bi/login",
                "/ibmcognos/bi/v1/login",
                "/api/login"  # This appears to be used in your environment
            ]
            
            # For each possible login endpoint, try to authenticate
            for endpoint in login_endpoints:
                try:
                    login_url = cognos_url
                    if not any(cognos_url.endswith(ep) for ep in login_endpoints):
                        base_url = cognos_url.split("?")[0].rstrip("/")
                        login_url = f"{base_url}{endpoint}"
                    
                    logging.info(f"Attempting login at: {login_url}")
                    
                    # Extract username/password or use namespace authentication
                    auth_data = {}
                    
                    # Handle different credential formats
                    if "username" in creds and "password" in creds:
                        auth_data = {
                            "username": creds["username"],
                            "password": creds["password"]
                        }
                        
                        # Add namespace if available
                        if "namespace" in creds:
                            auth_data["namespace"] = creds["namespace"]
                    
                    # If credentials don't match expected format, try the raw format
                    if not auth_data:
                        auth_data = creds
                    
                    login_response = session.post(
                        login_url,
                        json=auth_data,
                        headers={"Content-Type": "application/json"},
                        verify=False,
                        allow_redirects=True
                    )
                    
                    logging.info(f"Login response status: {login_response.status_code}")
                    
                    # Check if login was successful
                    if login_response.status_code < 400:
                        passport_id = check_for_passport_id(session, login_response)
                        if passport_id:
                            return passport_id
                    
                except requests.exceptions.RequestException as e:
                    logging.warning(f"Login attempt failed at {login_url}: {e}")
                    continue
            
            logging.error("Authentication failed with all known endpoints")
            return None
            
        return None
        
    except requests.exceptions.RequestException as e:
        logging.error(f"Error connecting to Cognos: {e}")
        return None

def check_for_passport_id(session, response):
    """Check for passport ID in cookies and response headers/content"""
    # Check cookies for camPassportId
    for cookie in session.cookies:
        if 'passport' in cookie.name.lower():
            passport_id = cookie.value
            logging.info(f"Found {cookie.name}: {passport_id[:10]}...")
            return passport_id
    
    # Check response headers for redirects containing passport
    if 'Location' in response.headers:
        redirect_url = response.headers['Location']
        if 'cmpassport=' in redirect_url.lower():
            passport_id = redirect_url.split('cmpassport=')[1].split('&')[0]
            logging.info(f"Found passport in redirect: {passport_id[:10]}...")
            return passport_id
    
    # Check response body for passport ID in various formats
    try:
        content_type = response.headers.get('Content-Type', '')
        if 'json' in content_type:
            data = response.json()
            if 'camPassportId' in data:
                passport_id = data['camPassportId']
                logging.info(f"Found passport in JSON response: {passport_id[:10]}...")
                return passport_id
    except Exception:
        pass
    
    # Check if response text contains passport ID patterns
    try:
        text = response.text
        if 'passport' in text.lower():
            import re
            # Try to find passport ID using regex
            match = re.search(r'["\']?cm:passport["\']?\s*[:=]\s*["\']([^"\']+)["\']', text)
            if match:
                passport_id = match.group(1)
                logging.info(f"Found passport in response text: {passport_id[:10]}...")
                return passport_id
    except Exception:
        pass
    
    return None

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

def get_cognos_session_for_deployment(cognos_url, credentials=None, credentials_file=None):
    """
    Get Cognos session information suitable for MotioCI deployment.
    
    Returns:
        dict: Dictionary with authentication information including camPassportId
    """
    passport_id = get_cam_passport_id(cognos_url, credentials, credentials_file)
    
    if passport_id:
        return {
            'camPassportId': passport_id
        }
    return None 
