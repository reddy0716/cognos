## GraphQL API

#### What is GraphQL?

* Getting started with GraphQL: https://graphql.org/

#### Using MotioCI's GraphQL API

* GraphiQL
   * MotioCI includes a GraphQL Web IDE that allows users to interact with the GraphQL API as well as view the documentation for the API.
   * To access it, go to (https://ciServerUrl/graphiql/index.html)

## MotioCI GraphQL API Samples

This is a sample GraphQL API client written in Python. This provides a simple command line interface allowing users to create and deploy labels between Cognos environments. Samples are intended as a starting point and are provided as is.

### Prerequisites
* Minimum Python version 3.8.0 or higher
* Install Python at https://www.python.org/downloads **IMPORTANT: During installation, make sure to add Python to PATH and install pip**
* To verify installation, type `python` in a terminal and verify it returns version info.

### Troubleshooting

* If 'python --version' does not return 3.8.0 or higher, try using 'py -3' instead of 'python' for the CLI commands.

### Setup

1. Open a new terminal and cd to the CLI directory. Optionally: Move/Copy CLI folder to a different location.
2. Run the command:
```sh
pip install -r requirements.txt
```
3. Once that is completed, the MotioCI CLI tool is ready to run API commands.

### Example commands

#### Login Command

Purpose: Generate a xauthtoken which is needed to run commands using the CLI.

Information about instances and namespaces can be gathered through the GraphiQL Web IDE using this query:

```graphql
query getInstanceAndNamespaceInformation {
  instances {
    edges{
      node {
        id
        name
        namespaces {
          id
        }
      }
    }
  }
}
```

First, we'll need to create a credentials string to authenticate against CI/Cognos. This is a JSON array of credentials for 1 or more Cognos instances:

```json
[ 
    {
        "namespaceId":"xmlcap",
        "username":"jdoe",
        "password":"mYp@ssword",
        "instanceId":"1"},
    {
        "namespaceId":"xmlcap",
        "username":"jdoe",
        "password":"mYp@ssword",
        "instanceId":"2"},
    {
        "namespaceId":"xmlcap",
        "camPassportId":"123582103948340982134098l",
        "instanceId":"3"
    }
]
```

These can either be username/password credentials or a camPassport retrieved via the Cognos SDK.

**In all examples below, replace server parameter with your server URL and credentials parameter with your credentials.**

1. The login example below works on UNIX based systems.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" login --credentials '[{"namespaceId":"xmlcap","username":"jdoe","password":"mYp@ssword","instanceId":"1"},{"namespaceId":"xmlcap","username":"jdoe","password":"mYp@ssword","instanceId":"2"}]'
```
2. The login example below works on cmd on Windows.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" login --credentials [{\"namespaceId\":\"xmlcap\",\"username\":\"jdoe\",\"password\":\"mYp@ssword\",\"instanceId\":\"1\"},{\"namespaceId\":\"xmlcap\",\"username\":\"jdoe\",\"password\":\"mYp@ssword\",\"instanceId\":\"2\"}]
```
3. On either UNIX or Windows systems, instead of directly passing the credentials, the script prompts the user to enter credentials. Allows user to login up to two instances.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" login
```

#### Deployment Command

Purpose: deploy a label.

Information about projects and labels can be gathered through the GraphiQL Web IDE using this query:

```graphql
query getAllAvailableLabelVersions {
  instances {
    edges {
      node {
        __typename
        name
        id
        projects {
          edges {
            node {
              __typename
              name
              id
              labels {
                edges {
                  node {
                    __typename
                    name
                    id
                    labelVersions {
                      edges {
                        node {
                          __typename
                          id
                          version
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

**In all examples below, replace server parameter with your server URL and xauthtoken parameter with your valid xauthtoken from the login command.**

* Other required arguments include one of (1.labelId 2.labelVersionId 3.versionedItemIds 4.searchPath), one of (1.targetInstanceId 2.targetInstanceName 3.targetUnversionedInstanceId 4.targetUnversionedInstanceName), and one of (1.camPassportId 2.username).
* All other arguments are optional or queried to the user
* The following values can also be provided and are optional: sourceInstanceId, sourceInstanceName, targetLabelName, projectName, projectId, password, namespaceId.
* In order to deploy between instances, user must be logged into source and target instances using the login command.
* Requires authentication for deployment. Use either Portal authentication (pass camPassportId parameter) or Standard authentication (pass username, namespaceId, and password parameters).
* The deploy script assumes both the source and target project names are the same. If not, there will be a NoneType error thrown. This is a Python limitation and not a CLI limitation.

1. deploy given labelId. Portal Authentication example.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" deploy --xauthtoken=4a1a43e5-5ec2-40dd-94c3-1d6be05f2d3d --sourceInstanceId=1 --labelId=6 --targetInstanceId=1 --targetLabelName="myLabel" --camPassportId="CAMPASSPORTID"
```
2. deploy given labelVersionId. Standard Authentication example.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" deploy --xauthtoken=4a1a43e5-5ec2-40dd-94c3-1d6be05f2d3d --targetInstanceName="Cognos Prod" --targetLabelName="myLabel" --labelVersionId=24 --username="jdoe" 
```
3. deploy given searchPath.
   * The searchPath parameter takes the value of the raw path of an object versioned in MotioCI.
   * Able to provide multiple space-seperated paths.
   * A path can point to a folder/report or any cognos object that can be deployd from a label.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" deploy --xauthtoken=4a1a43e5-5ec2-40dd-94c3-1d6be05f2d3d --sourceInstanceName="Cognos Dev" --targetInstanceName="Cognos Prod" --projectName="Admin" --targetLabelName="Shared Files Label" --searchPath "/content/folder[@name='Motio Samples']" "/content/folder[@name='Motio Samples']/URL[@name='Test URL']" --camPassportId="CAMPASSPORTID"
```
4. deploy given versionedItemIds.
   * The versionedItemIds is the id of a specific version of an object.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" deploy --xauthtoken=4a1a43e5-5ec2-40dd-94c3-1d6be05f2d3d --sourceInstanceName="Cognos Dev" --targetInstanceName="Cognos Prod" --projectName="Admin" --targetLabelName="Shared Files Label" --versionedItemIds="[11, 12]" --camPassportId="CAMPASSPORTID"
```
5. deploy given any and however many of the optional parameters
   * If label exists, deploys existing label. If label does not exist, creates a new label with searchPath and versionedItemIds then deploys that new label.
   * Keep in mind that required options are mutually exclusive (i.e. can't do both searchPath and versionedItemIds)
   * versionedItemIds value can be for different objects to be deployd.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" deploy --xauthtoken=4a1a43e5-5ec2-40dd-94c3-1d6be05f2d3d --sourceInstanceName="Cognos Dev" --sourceInstanceId=1 --targetInstanceName="Cognos Prod" --projectName="Admin" --targetLabelName="Shared Files Label" --searchPath "/content/folder[@name='Motio Samples']" "/content/folder[@name='Motio Samples']/URL[@name='Test URL']" --camPassportId="CAMPASSPORTID"
```
6. deploy to an unversioned instance.
   * Pass either targetUnversionedInstanceId or targetUnversionedInstanceName
   * If given targetLabelName, it will not be used
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" deploy --xauthtoken=4a1a43e5-5ec2-40dd-94c3-1d6be05f2d3d --targetUnversionedInstanceName="Cognos QA" --labelVersionId=24 --username="jdoe"
```

#### Help Commands

Purpose: Provide information over available commands and parameters that are integrated in the CLI.

* To access the additional information, add the -h flag to the command.
* Must provide a server URL but does not require a xauthtoken.
1. If user wants more information on available subjects such as login, logout, instance, etc.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" -h
```
2. If user wants more information on available verbs such as ls, deploy (labelVersion only), etc.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" label -h
```
3. If user wants more information on available arguments to pass for a specific command.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" label create -h
```

#### GraphiQL Queries
* Raw GraphiQL queries used for the CLI are available to view in the queries.txt file.
  --s
### Additional Commands

**In all examples below, replace server parameter with your server URL and xauthtoken parameter with your valid xauthtoken from the login command.**

#### Project ls Command
Purpose: Long listing of projects.

1. List all projects.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" project ls --xauthtoken=87a2470f-cb62-4662-98cf-c0e2bfc92184
```

2. List all projects for a specific instance.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" project ls --xauthtoken=87a2470f-cb62-4662-98cf-c0e2bfc92184 --instanceName="Cognos Dev"
```

#### Label ls Command
Purpose: Long listing of labels.

1. List all labels.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" label ls --xauthtoken=87a2470f-cb62-4662-98cf-c0e2bfc92184
```

2. List all labels for a specific instance and project
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" label ls --xauthtoken=87a2470f-cb62-4662-98cf-c0e2bfc92184 --instanceName="Cognos Dev" --projectName="Admin"
```

#### Label Create Command
Purpose: Create a label with versionedItemIds.

```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" label create --xauthtoken=87a2470f-cb62-4662-98cf-c0e2bfc92184 --instanceName="Cognos Dev" --projectName="Admin" --name="Cheese" --versionedItemIds="[11,12]"
```

#### Label Version ls Command
Purpose: Long listing of label versions.

```sh
1. List all label versions.
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" labelVersion ls --xauthtoken=87a2470f-cb62-4662-98cf-c0e2bfc92184
```

2. List all label versions for a specific instance, project, and label.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" labelVersion ls --xauthtoken=87a2470f-cb62-4662-98cf-c0e2bfc92184 --instanceName="Cognos Dev" --projectName="Admin" --targetLabelName="Shared Files Label"
```

#### Versioned Item ls Command
Purpose: Long listing of versioned items.
```sh
1. List all versioned items.
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" versionedItems ls --xauthtoken=87a2470f-cb62-4662-98cf-c0e2bfc92184
```

2. List all versioned items for a specific instance, project, and searchPath. The user can choose to display only non-deleted versioned items by setting currentOnly to true.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" versionedItems ls --xauthtoken=87a2470f-cb62-4662-98cf-c0e2bfc92184 --instanceName="Cognos Dev" --projectName="Admin" --searchPath="starts:/content/" --currentOnly=True
```

#### Logout Command
Purpose: Logout out of CI/Cognos instances.
* After execution, the provided xauthtoken will no longer be valid.
```sh
python ci-cli.py --server="http://ci-docker.dallas.motio.com:8080" logout --xauthtoken=87a2470f-cb62-4662-98cf-c0e2bfc92184
```
