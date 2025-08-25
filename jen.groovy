def complete_standard_auth(namespaces, args):
    # Build list of valid namespace ids reported by the target instance
    valid_namespaces = [ns['id'] for ns in namespaces]

    # Password
    if args.password is None:
        print("Username Entered: ", args.username)
        password = input("Enter Password: ")
    else:
        password = args.password

    # Namespace selection (order: CLI flag -> env var -> 'azure' if present -> prompt)
    namespace_id_input = args.namespaceId or os.environ.get("NAMESPACE_ID")
    if not namespace_id_input and "azure" in valid_namespaces:
        namespace_id_input = "azure"

    # Validate or prompt until valid
    while not namespace_id_input or namespace_id_input not in valid_namespaces:
        print("Invalid or missing namespaceId. Available namespaces:", valid_namespaces)
        namespace_id_input = input("Enter namespaceId (or 'q' to quit): ")
        if namespace_id_input == "q":
            break

    authentication = {
        'password': {
            'namespaceId': namespace_id_input,
            'username': args.username,
            'password': password
        }
    }
    return authentication

def get_authentication(target_instance_id, is_versioned, args):
    if args.camPassportId is not None:
        authentication = {'camPassportId': args.camPassportId}
    elif args.username is not None:
        valid_namespaces = get_available_namespaces(target_instance_id, is_versioned)
        authentication = complete_standard_auth(valid_namespaces, args)
    else:
        print("Error. Missing deployment credentials. Enter either camPassportId for portal auth or username for standard auth")
        return
    return authentication
