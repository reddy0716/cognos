import groovy.json.JsonSlurper
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials


def MOTIO_SERVER = "https://cgrptmcip01.cloud.cammis.ca.gov"
def SOURCE_ENV = SOURCE_ENV ?: "Cognos-DEV/TEST"
def WORKSPACE = "/var/lib/jenkins/workspace/MotioCI/api/CLI"
def CRED_ID = "cognos-credentials"


def projects = []
def TOKEN = ""

def creds = CredentialsProvider.lookupCredentials(
        StandardUsernamePasswordCredentials.class,
        Jenkins.instance,
        null,
        null
).find { it.id == CRED_ID }

if (!creds) {
    return ["Credential '${CRED_ID}' not found in Jenkins"]
}
def USER = creds.username
def PASS = creds.password.plainText

try {
    
    def loginCmd = [
        "bash", "-c",
        """
        cd ${WORKSPACE} && \
        python3 ci-cli.py --server=${MOTIO_SERVER} login \
          --username ${USER} --password ${PASS} > login.out 2>&1 && \
        awk 'match(\$0,/(xauthtoken|Auth[[:space:]]*Token)[[:space:]]*[:=][[:space:]]*([A-Za-z0-9._-]+)/,m){print m[2]}' login.out | tail -n1
        """
    ]
    def loginProc = loginCmd.execute()
    def loginOut = new StringBuffer()
    loginProc.consumeProcessOutput(loginOut, new StringBuffer())
    loginProc.waitFor()

    TOKEN = loginOut.toString().trim()
    if (!TOKEN || TOKEN.length() < 10) {
        return ["Failed to obtain MotioCI token — check credentials or CLI path"]
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
        return ["No response from MotioCI — check server/network"]
    }

    def data = new JsonSlurper().parseText(outputText)
    def edges = data?.data?.instances?.edges ?: []
    edges.each { e ->
        e?.node?.projects?.edges?.each { p ->
            def name = p?.node?.name
            if (name) projects << name
        }
    }

    return projects.isEmpty() ? ["No projects found for ${SOURCE_ENV}"] : projects.unique().sort()

} catch (Exception e) {
    return ["Error fetching projects: ${e.message}"]
}
