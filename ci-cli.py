import json
import argparse
import ast
import login
import projects
import versioned_items
import labels
import label_versions
import deployment
import requests
import constants
import instances
import loginInput
import queries
import os
import urllib3
import ssl
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# Monkey patch requests to disable SSL verification globally
import requests
original_post = requests.post
def patched_post(*args, **kwargs):
    kwargs['verify'] = False
    return original_post(*args, **kwargs)
requests.post = patched_post


def mask_credentials_for_logging(credentials_string):
    """Safely mask credentials for logging"""
    try:
        creds = json.loads(credentials_string)
        masked_creds = []
        for cred in creds:
            masked_cred = {}
            for key, value in cred.items():
                if key.lower() in ['apikey', 'password', 'token', 'secret']:
                    masked_cred[key] = '*' * 8
                else:
                    masked_cred[key] = value
            masked_creds.append(masked_cred)
        return json.dumps(masked_creds)
    except:
        return "*** MASKED CREDENTIALS ***"

# Method for loading credentials from JSON file
def load_credentials_from_json(file_path="credentials.json"):
    """
    Load credentials from a JSON file.
    
    Args:
        file_path (str): Path to the credentials JSON file
        
    Returns:
        str: JSON string of credentials or None if file not found/invalid
    """
    try:
        if not os.path.exists(file_path):
            print(f"ERROR: Credentials file '{file_path}' not found!")
            return None
            
        with open(file_path, 'r') as file:
            credentials_data = json.load(file)
            
        # Convert back to JSON string format expected by login_init
        credentials_json = json.dumps(credentials_data)
        
        print(f"Successfully loaded credentials from {file_path}")
        return credentials_json
        
    except json.JSONDecodeError as e:
        print(f"ERROR: Invalid JSON format in credentials file - {e}")
        return None
    except Exception as e:
        print(f"ERROR: Failed to load credentials file - {e}")
        return None


# Method for parsing command line parameters
def parse_args():
    # Format for command line: subject verb flags
    # Options for first positional argument: login, label, labelVersion, project, versionedItems, instance, logout
    # Options for second positional argument: based on subject. Ex. label allows ls or create. instance allows only ls.
    parent_parser = argparse.ArgumentParser(
        description="description: sample Python CLI to perform queries using the GraphiQL API.")
    parent_parser.add_argument('--server', type=str, required=True,
                               help="provide link to GraphiQL API for the commands to run")
    subject_parser = parent_parser.add_subparsers(dest="subject",
                                                  description='''subjects description: The available subjects are stated below. To find out what actions can be performed on a subject, run python ci-cli.py [subject] -h. To run commands, the xauthtoken and server flags are required.''')

    # Subparser for login
    login_parser = subject_parser.add_parser('login',
                                             description='''description: Login to MotioCI. There are three ways to run this command. 1. If no arguments are provided, the CLI will prompt the user to enter login information of instance name, namespace id, username and password. 2. The credentials flag and a properly formatted credentials string is provided. 3. The credentialsFile flag to load credentials from a JSON file. Examples of the login command using the credentials flag in either a windows/unix terminal are provided in the README.txt ''',
                                             help="login to MotioCI. Generates authtoken that will be used to run commands in the CLI.")
    login_parser.add_argument('--credentials', type=str,
                              help="alternate method of login. Allows user to login using a properly formatted credentials string.",
                              metavar='')
    login_parser.add_argument('--credentialsFile', type=str, default="credentials.json",
                              help="path to JSON file containing credentials (default: credentials.json)",
                              metavar='')

    # Subparser for instance subject. Commands available: ls
    instance_subparser = subject_parser.add_parser("instance", help="perform queries on instances.",
                                                   description="description: perform queries on instances.")
    instance_verb_subparser = instance_subparser.add_subparsers(dest="verb")
    instance_ls_parser = instance_verb_subparser.add_parser("ls",
                                                            help="list all instances that the user has access to.",
                                                            description='''description: List instances. There is only one way to run this command. 1. When no arguments are present, list all instances that the user has access to. ''')
    instance_ls_parser.add_argument('--xauthtoken', type=str, required=True,
                                    help="login token given after performing login command correctly.")

    # Subparser for project subject. Commands available: ls
    project_subparser = subject_parser.add_parser("project", help="perform queries on projects.",
                                                  description="description: perform queries on projects.")
    project_verb_subparser = project_subparser.add_subparsers(dest="verb")
    project_ls_parser = project_verb_subparser.add_parser("ls",
                                                          description='''description: List projects. There are two ways to run this command. 1. When no arguments are present, list all projects that the user has access to. 2. With argument instanceName present, list only projects that exist within the instance that matches the instanceName.''',
                                                          help="list all projects that the user has access to.")
    project_ls_parser.add_argument('--xauthtoken', type=str, required=True,
                                   help="login token given after performing login command correctly.")
    project_ls_parser.add_argument('--instanceName', type=str, help="query based on specific instance name.",
                                   metavar='')

    # Subparsers for label subject. Commands available: create and ls

    label_subparser = subject_parser.add_parser("label", help="perform queries/mutation on labels.",
                                                description="description: perform queries/mutation on labels.")
    label_verb_subparser = label_subparser.add_subparsers(dest="verb")
    label_create_parser = label_verb_subparser.add_parser("create", help="create a new label.",
                                                          description='''
                                                          description: Create a new label. There is only one way to run this command. 1. With arguments instanceName, projectName, name,
                                                          and versionedItemIds present, create a new label from the given arguments. ''')
    label_create_parser.add_argument('--xauthtoken', type=str, required=True,
                                     help="login token given after performing login command correctly.")
    label_create_parser.add_argument('--instanceName', type=str, metavar='', help="used to find instance id.")
    label_create_parser.add_argument('--projectName', type=str, metavar='', help="used to find project id.")
    label_create_parser.add_argument('--name', type=str, metavar='', help="creates a label with this name.")
    label_create_parser.add_argument('--versionedItemIds', type=str, metavar='',
                                     help="list of items to include under the new label.")
    label_ls_parser = label_verb_subparser.add_parser("ls", help="list all labels that the user has access to.",
                                                      description='''description: List labels. There are three ways to run this command. 1. When no arguments are present, list all labels that the user has access to. 2. With arguments instanceName and projectName present, list all labels within the given instance and project. 3. With argument labelName present, list the label with matching labelName.''')
    label_ls_parser.add_argument('--xauthtoken', type=str, required=True,
                                 help="login token given after performing login command correctly.")
    label_ls_parser.add_argument('--instanceName', type=str, help="query based on specific instance name.", metavar='')
    label_ls_parser.add_argument('--projectName', type=str, help="query based on specific project name.", metavar='')
    label_ls_parser.add_argument('--labelName', type=str, help="query based on specific label name.", metavar='')

    # Subparsers for label version subject. Commands available: promote and ls
    label_version_subparser = subject_parser.add_parser("labelVersion",
                                                        description="description: perform queries/mutation on label versions.",
                                                        help="perform queries/mutation on label versions.")
    label_version_verb_subparser = label_version_subparser.add_subparsers(dest="verb")
    label_version_ls_parser = label_version_verb_subparser.add_parser("ls",
                                                                      help="list label versions that the user has access to.",
                                                                      description='''
                                                        description: List label versions.There are two ways to call this verb: 1.With no arguments present,
                                                        list all of the label versions that the user has access to.2.With arguments
                                                         instanceName, projectName, and labelName present, lists the label versions that fit the criteria within the instance, project, and label names. 
                                                        ''')

    label_version_ls_parser.add_argument('--xauthtoken', type=str, required=True,
                                         help="login token given after performing login command correctly.")
    label_version_ls_parser.add_argument('--instanceName', type=str, help="query based on specific instance name.",
                                         metavar='')
    label_version_ls_parser.add_argument('--projectName', type=str, help="query based on specific project name.",
                                         metavar='')
    label_version_ls_parser.add_argument('--labelName', type=str, help="query based on specific label name.",
                                         metavar='')

    # Subparser for deployment.
    deployment_parser = subject_parser.add_parser("deploy", help="Execute a deployment.",
                                                  description=''' description: deploy a label. There are two ways to run this command. 1.if no arguments are provided, the program will automatically prompt the user to input relevant information to perform the query. 2.With some or all arguments given, the CLI will prompt user to input critical information such as target instance id and label version id.''')

    deployment_parser.add_argument('--xauthtoken', type=str, required=True,
                                   help="login token given after performing login command correctly.")
    deployment_parser.add_argument('--sourceInstanceId', type=int, metavar='', help="specify source instance.")
    deployment_parser.add_argument('--sourceInstanceName', type=str, metavar='',
                                   help="used to find source instance id.")

    label_source_group = deployment_parser.add_mutually_exclusive_group(required=True)
    label_source_group.add_argument('--labelId', type=int, metavar='', help="specify label.")
    label_source_group.add_argument('--labelVersionId', type=int, metavar='',
                                    help="used to find specific label version.")
    label_source_group.add_argument('--versionedItemIds', type=str, metavar='',
                                    help="create a new label with these versioned items")
    label_source_group.add_argument('--searchPath', nargs='+', type=str, action='append', metavar='',
                                    help="used to find versioned items located by path")

    target_instance_group = deployment_parser.add_mutually_exclusive_group(required=True)
    target_instance_group.add_argument('--targetInstanceId', type=int, metavar='', help="specify target instance.")
    target_instance_group.add_argument('--targetInstanceName', type=str, metavar='',
                                       help="used to find target instance id.")
    target_instance_group.add_argument('--targetUnversionedInstanceId', type=int, metavar='',
                                       help="specify unversioned target instance.")
    target_instance_group.add_argument('--targetUnversionedInstanceName', type=str, metavar='',
                                       help="used to find unversioned target instance id.")

    deployment_parser.add_argument('--targetLabelName', type=str, metavar='',
                                   help="Name of the label to create in the target project.")
    deployment_parser.add_argument('--projectName', type=str, metavar='', help="Name of the target project")
    deployment_parser.add_argument('--projectId', type=int, metavar='', help="Name of the target project")

    authentication_group = deployment_parser.add_mutually_exclusive_group(required=True)
    authentication_group.add_argument('--camPassportId', type=str, metavar='', help="used for portal authentication")
    authentication_group.add_argument('--username', type=str, metavar='', help="used for standard authentication")

    deployment_parser.add_argument('--password', type=str, metavar='', help="used for standard authentication")
    deployment_parser.add_argument('--namespaceId', type=str, metavar='', help="used for standard authentication")

    # Subparser for versioned item subject. Commands available: ls
    versioned_item_subparser = subject_parser.add_parser("versionedItems",
                                                         help="perform queries on versioned items.")
    versioned_item_verb_subparser = versioned_item_subparser.add_subparsers(dest="verb")
    versioned_item_ls_parser = versioned_item_verb_subparser.add_parser("ls", help='''
        list versioned items content.''',
                                                                        description=''' description: list versioned items. There are two ways to call this verb: 1.With no arguments present, list all of the label versions that the user has access to. 2.With arguments instanceName, projectName, searchPath, and currentOnly present, list the label versions with match the criteria. The search path has to be formatted in a special way: 'operator:path/to/the/file'. The operator can be starts, contains, equals, ends, between, and in.''')

    versioned_item_ls_parser.add_argument('--xauthtoken', type=str, required=True,
                                          help="login token given after performing login command correctly.")
    versioned_item_ls_parser.add_argument('--instanceName', type=str, help="query based on specific instance name.",
                                          metavar='')
    versioned_item_ls_parser.add_argument('--projectName', type=str, help="query based on specific project name.",
                                          metavar='')
    versioned_item_ls_parser.add_argument('--searchPath', type=str,
                                          help="query for versioned items based on their path in CI. Able to input many search paths with a space between them.",
                                          metavar='')
    versioned_item_ls_parser.add_argument('--currentOnly', type=bool,
                                          help="determines what types of versioned items are displayed. if true, display only non-deleted versioned items. if false, display all versioned items.",
                                          metavar='')

    # Subparser for logout subject.
    logout_subparser = subject_parser.add_parser("logout",
                                                 help="logout of MotioCI. The authtoken will be unusable afterwards.",
                                                 description='''logout of MotioCI. There is only one way to run this command. With no arguments, this will log you out of MotioCI and the authtoken will be unusable afterwards ''')
    logout_subparser.add_argument('--xauthtoken', type=str, required=True,
                                  help="login token given after performing login command correctly.")
    return parent_parser.parse_args()


def run_deployment(args):
    # Get Source Input
    # DeploymentSourceInput.instanceId always required
    if args.sourceInstanceId is None:
        source_instance_id = find_instance_id(args.sourceInstanceId, args.sourceInstanceId,
                                              "Enter Source Instance Name/Id: ")
    else:
        source_instance_id = args.sourceInstanceId

    # DeploymentSourceInput.label. Mutually Exclusive Options.
    if args.labelVersionId is not None:
        source_label_input = {'labelVersionId': args.labelVersionId}
    elif args.labelId is not None:
        source_label_input = {'labelId': args.labelId}
    elif args.versionedItemIds is not None:
        project_id = find_project_id(source_instance_id, args.projectId, args.projectName, "Enter Project Name/Id: ")
        ids_input_string = str(args.versionedItemIds)[1:-1] # Trim brackets
        versionedItemIds = [int(versionedItemId.strip()) for versionedItemId in ids_input_string.split(",")] # Isolate IDs into array of integers
        source_label_input = {'adHoc': {'versionedItemIds': versionedItemIds, 'projectId': project_id}}
    elif args.searchPath is not None:
        project_id = find_project_id(source_instance_id, args.projectId, args.projectName, "Enter Project Name/Id: ")
        source_label_input = {'adHoc': {'versionedItemIds': get_versioned_items_from_searchpaths(args, project_id),
                                        'projectId': project_id}}
    else:
        print("Error. Missing source input. Enter labelVersionId, labelId, versionedItemIds, or searchPath.")
        return

    # Get Target Input
    if args.targetInstanceId is not None or args.targetInstanceName is not None:
        if args.targetLabelName is not None:
            target_instance_id = find_instance_id(args.targetInstanceId, args.targetInstanceName,
                                                  "Enter Target Instance Name/Id:")
            deployment_target_input = {'instance': {'id': target_instance_id,
                                                    'authentication': get_authentication(target_instance_id, True,
                                                                                         args),
                                                    'labelName': args.targetLabelName}}
        else:
            print("Error. Missing target label name.")
            return
    elif args.targetUnversionedInstanceId is not None or args.targetUnversionedInstanceName is not None:
        target_instance_id = find_unversioned_instance_id(args.targetUnversionedInstanceId,
                                                          args.targetUnversionedInstanceName,
                                                          "Enter Target Unversioned Instance Name/Id:")
        deployment_target_input = {'unversionedInstance': {'id': target_instance_id,
                                                           'authentication': get_authentication(target_instance_id,
                                                                                                False, args)}}
    else:
        print(
            "Error. Missing target input. Enter targetInstanceId, targetInstanceName, targetUnversionedInstanceId, or targetUnversionedInstanceName.")
        return

    deployment.deploy(source_label_input, source_instance_id, deployment_target_input)
    return


def get_available_namespaces(target_instance_id, isVersioned):
    variables = {'id': target_instance_id}
    if isVersioned:
        response = requests.post(constants.GRAPH_URL, headers={'x-auth-token': constants.X_AUTH_TOKEN},
                                 json={'query': queries.GET_VERSIONED_NAMESPACES, 'variables': variables})
        namespaces = response.json()["data"]["instance"]["namespaces"]
    else:
        response = requests.post(constants.GRAPH_URL, headers={'x-auth-token': constants.X_AUTH_TOKEN},
                                 json={'query': queries.GET_UNVERSIONED_NAMESPACES, 'variables': variables})
        namespaces = response.json()["data"]["unversionedInstance"]["namespaces"]
    return namespaces

    # Get target authentication method


def get_authentication(target_instance_id, is_versioned, args):
    if args.camPassportId is not None:
        authentication = {'camPassportId': args.camPassportId}
    elif args.username is not None:
        valid_namespaces = get_available_namespaces(target_instance_id, is_versioned)
        authentication = complete_standard_auth(valid_namespaces, args)
    else:
        print(
            "Error. Missing deployment credentials. Enter either camPassportId for portal auth or username for standard auth");
        return
    return authentication


def complete_standard_auth(namespaces, args):
    valid_namespaces = []
    for namespace in namespaces:
        valid_namespaces.append(namespace['id'])
    if args.password is None:
        print("Username Entered: ", args.username)
        password = input("Enter Password: ")
    else:
        password = args.password
    if args.namespaceId is None:
        namespace_id_input = input("Enter namespaceId: ")
        while namespace_id_input not in valid_namespaces and namespace_id_input != "q":
            print("Invalid namespace. Available namespaces: ", valid_namespaces)
            namespace_id_input = input("Enter namespaceName (enter q to quit): ")
    else:
        namespace_id_input = args.namespaceId
    authentication = {'password': {'namespaceId': namespace_id_input, 'username': args.username, 'password': password}}
    return authentication


# Loop for instances' id, name, or valid user input. Validates user input on possible options in CI
def find_instance_id(instance_id, instance_name, input_string):
    outputOptionsAvailable = False
    instance_array = instances.get_instances_default()
    while True:
        if instance_id is None:
            if instance_name is None:
                if outputOptionsAvailable is True:
                    instance_array = instances.get_instances_default()
                    print("Error! Incorrect Input!")
                    print("Available entries: ", end="")
                    instances.print_instances(instance_array)
                    print("")
                user_input = input(input_string)
                outputOptionsAvailable = True
                instance_id, instance_name = assign_input(user_input)

        if instance_id is not None:
            if instances.check_if_valid_instance_id(instance_array, instance_id):
                break

        elif instance_name is not None:
            if instances.check_if_valid_instance_name(instance_array, instance_name):
                instance_id = instances.get_instance_id(instance_name)
                break

        instance_id = None
        instance_name = None
    return instance_id


# Loop for instances' id, name, or valid user input. Validates user input on possible options in CI
def find_unversioned_instance_id(instance_id, instance_name, input_string):
    outputOptionsAvailable = False
    instance_array = instances.get_unversioned_instances_default()
    while True:
        if instance_id is None:
            if instance_name is None:
                if outputOptionsAvailable is True:
                    instance_array = instances.get_unversioned_instances_default()
                    print("Error! Incorrect Input!")
                    print("Available entries: ", end="")
                    instances.print_instances(instance_array)
                    print("")
                user_input = input(input_string)
                outputOptionsAvailable = True
                instance_id, instance_name = assign_input(user_input)

        if instance_id is not None:
            if instances.check_if_valid_instance_id(instance_array, instance_id):
                break

        elif instance_name is not None:
            if instances.check_if_valid_instance_name(instance_array, instance_name):
                instance_id = instances.get_unversioned_instance_id(instance_name)
                break

        instance_id = None
        instance_name = None
    return instance_id


# Loop for projects' id, name, or valid user input. Validates user input on possible options in CI
def find_project_id(source_instance_id, project_id, project_name, input_string):
    outputOptionsAvailable = False
    project_list = projects.get_valid_projects(source_instance_id)
    while True:
        if project_id is None:
            if project_name is None:
                if outputOptionsAvailable is True:
                    print("Error! Incorrect Input!")
                    print("Available entries: ", end="")
                    project_list = projects.get_valid_projects(source_instance_id)
                    projects.print_projects(project_list)
                    print("")
                user_input = input(input_string)
                outputOptionsAvailable = True
                project_id, project_name = assign_input(user_input)

        if project_id is not None:
            if projects.check_if_valid_project_id(project_list, project_id):
                break

        elif project_name is not None:
            if projects.check_if_valid_project_name(project_list, project_name):
                project_id = projects.get_project_id(source_instance_id, project_name)
                break

        project_id = None
        project_name = None
    return project_id


# Sets id if userinput is type int. Else sets name
def assign_input(user_input):
    try:
        int_input = int(user_input)
        return int_input, None
    except ValueError:
        return None, user_input


# Loop for user input of versioned_item_ids
def get_user_input_versioned_item_ids():
    versioned_item_ids = []
    print("Enter versionedItemIds. Press q to stop:")
    curId = ""
    while True:
        curId = input("Enter id:")
        if curId == "q":
            break
        response = versioned_items.get_versioned_item_by_id(int(curId))
        if response["data"]["versionedItem"] is None:
            print("Bad versionedItemId, enter another one")
            continue
        versioned_item_ids.append(int(curId))
    return versioned_item_ids


# Retrieve versioned_items . Ask for user input if both fields are None
def get_versioned_items_from_searchpaths(args, project_id):
    versioned_item_ids = []
    # If search path is given, find versioned_item_ids
    versioned_item_ids.extend(ast.literal_eval(label_versions.get_version_ids(project_id, args.searchPath)))

    return versioned_item_ids


'''
List of if elif else statements for deciding which method to execute based on input.
args.subject options: login, instance, project, label, label version, versioned items, and logout
args.verb options: ls, create (label only), promote (label version only)
Each ls has a default option with no flags (except --authtoken) and a specific option with all flags needed to execute
Each create and promote label requires all flags. 
'''

args = parse_args()
constants.CI_URL = args.server
constants.LOGIN_URL = constants.CI_URL + constants.LOGIN_URL
constants.LOGOUT_URL = constants.CI_URL + constants.LOGOUT_URL
constants.GRAPH_URL = constants.CI_URL + constants.GRAPH_URL

if args.subject == "login":
    # Priority order: 1. credentials parameter, 2. credentialsFile parameter, 3. user input
    if args.credentials is not None:
        credentials_to_use = args.credentials
    else:
        credentials_to_use = load_credentials_from_json(args.credentialsFile)
        
        if credentials_to_use is None:
            print("Falling back to manual credential input...")
            credentials_to_use = loginInput.get_login_from_user()
    
    # SECURE: Only log masked credentials
    print("== DEBUG: Attempting login with masked credentials ==")
    print(mask_credentials_for_logging(credentials_to_use))
    
    # Attempt login
    auth_token = login.login_init(credentials_to_use)
    if auth_token is None:
        print("Invalid parameters. Login cancelled!")
    else:
        # SECURE: Don't log actual credentials, but DO show full auth token
        print("Login successful!")
        # Show full auth token - it's needed for subsequent operations
        print("x-auth_token: ", auth_token)

else:
    constants.X_AUTH_TOKEN = args.xauthtoken

if args.subject == "instance":
    if args.verb == "ls":
        print(instances.get_instances_default())
elif args.subject == "project":
    if args.verb == "ls":
        if args.instanceName is not None:
            projects.get_projects_specific(args.instanceName)
        else:
            projects.get_projects_default()
elif args.subject == "label":
    if args.verb == "create":
        labels.create_label_init(args.instanceName, args.projectName, args.name, args.versionedItemIds)
        print("Label Created!")
    elif args.verb == "ls":
        if args.labelName is not None:
            labels.get_label_specific(args.instanceName, args.projectName, args.labelName)
        elif args.instanceName is not None and args.projectName is not None:
            labels.get_labels_specific(args.instanceName, args.projectName)
        else:
            labels.get_labels_default()
elif args.subject == "deploy":
    run_deployment(args)
elif args.subject == "labelVersion":
    if args.verb == "ls":
        if args.instanceName is not None and args.projectName is not None and args.labelName is not None:
            label_versions.get_label_version_specific(args.instanceName, args.projectName, args.labelName)
        else:
            label_versions.get_label_version_default()

elif args.subject == "versionedItems":
    if args.verb == "ls":
        if args.instanceName is not None and args.projectName is not None and args.searchPath is not None and args.currentOnly is not None:
            versioned_items.get_versioned_items_specific(args.instanceName, args.projectName,
                                                         args.searchPath, args.currentOnly)
        else:
            versioned_items.get_versioned_items_default()
elif args.subject == "logout":
    response = requests.post(constants.LOGOUT_URL, headers={'x-auth-token': constants.X_AUTH_TOKEN})
    print("Logged out!")
