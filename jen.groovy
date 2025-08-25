if args.subject == "login":
    # Priority order: 1. --credentials, 2. --credentialsFile, 3. interactive
    if args.credentials is not None:
        credentials_to_use = args.credentials
    else:
        credentials_to_use = load_credentials_from_json(args.credentialsFile)
        if credentials_to_use is None:
            # In CI, this should fail fast; interactively you could fall back
            print("ERROR: No credentials provided and credentialsFile not found/invalid.", file=sys.stderr)
            sys.exit(1)

    token = login.login_init(credentials_to_use)
    if not token:
        print("ERROR: Login failed (no token returned).", file=sys.stderr)
        sys.exit(1)

    # IMPORTANT: print ONLY the token to stdout (no extra text)
    print(token)
    sys.exit(0)
else:
    constants.X_AUTH_TOKEN = args.xauthtoken
