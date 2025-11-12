import groovy.json.JsonSlurper

// MotioCI server and Jenkins paths
def MOTIO_SERVER = "https://cgrptmcip01.cloud.cammis.ca.gov"
def SOURCE_ENV = SOURCE_ENV ?: "Cognos-DEV/TEST"
def WORKSPACE = "/var/lib/jenkins/workspace/MotioCI/api/CLI"
def CREDENTIALS_FILE = "/var/lib/jenkins/credentials/cognos-credentials"

def projects = []
def TOKEN = ""

try {
  
    def loginCmd = [
        "bash", "-c",
        """
        cd ${WORKSPACE} && \
        python3 ci-cli.py --server=${MOTIO_SERVER} \
          login --credentialsFile ${CREDENTIALS_FILE} > login.out 2>&1 && \
        awk 'match(\$0,/(xauthtoken|Auth[[:space:]]*Token)[[:space:]]*[:=][[:space:]]*([A-Za-z0-9._-]+)/,m){print m[2]}' login.out | tail -n1
        """
    ]
  
    def loginProc = loginCmd.execute()
    def loginOut = new StringBuffer()
    loginProc.consumeProcessOutput(loginOut, new StringBuffer())
    loginProc.waitFor()

    TOKEN = loginOut.toString().trim()

    if (!TOKEN || TOKEN.length() < 10) {
        return ["Failed to get MotioCI token — verify credentials 'cognos-credentials'"]
    }
    def listCmd = [
        "bash", "-c",
        """
        cd ${WORKSPACE} && \
        python3 ci-cli.py --server=${MOTIO_SERVER} \
          project ls --xauthtoken ${TOKEN} \
          --instanceName ${SOURCE_ENV}
        """
    ]
  
    def listProc = listCmd.execute()
    def listOut = new StringBuffer()
    listProc.consumeProcessOutput(listOut, new StringBuffer())
    listProc.waitFor()

    def outputText = listOut.toString().trim()
    if (!outputText) {
        return ["No response from MotioCI — check network or permissions"]
    }
  
    def json
    try {
        json = new JsonSlurper().parseText(outputText)
    } catch (Exception parseEx) {
        return ["Unable to parse MotioCI output: ${parseEx.message}"]
    }

    def edges = json?.data?.instances?.edges ?: []
    edges.each { e ->
        e?.node?.projects?.edges?.each { p ->
            def name = p?.node?.name
            if (name) projects << name
        }
    }
  
    if (projects.isEmpty()) {
        return ["No projects found for ${SOURCE_ENV}"]
    }

    return projects.unique().sort()

} catch (Exception e) {
    return ["Error fetching projects: ${e.message}"]
}
