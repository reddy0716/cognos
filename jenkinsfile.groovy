import groovy.json.JsonSlurper
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardFileCredentials

// ===== CONFIGURATION =====
def MOTIO_SERVER = "https://cgrptmcip01.cloud.cammis.ca.gov"
def GIT_REPO     = "https://github.com/ca-mmis/combined-devops-cognos-deployments.git"
def SOURCE_ENV   = SOURCE_ENV ?: "Cognos-DEV/TEST"
def BASE_DIR     = "/var/lib/jenkins/workspace/MotioCI/api/CLI"
def CLI_FILE     = "${BASE_DIR}/ci-cli.py"
def REQ_FILE     = "${BASE_DIR}/requirements.txt"
def PYTHON       = "python3"
def CRED_ID      = "cognos-credentials"    // Jenkins File credential (credentials.json)
// ==========================

def projects = []
def TOKEN = ""


try {
    if (!new File(CLI_FILE).exists()) {
        println "[INFO] CLI not found, cloning repo..."
        def cloneCmd = [
            "bash","-c",
            """
            mkdir -p ${BASE_DIR} &&
            cd ${BASE_DIR}/.. &&
            git clone ${GIT_REPO} tmprepo --depth 1 &&
            cp -r tmprepo/MotioCI/api/CLI/* ${BASE_DIR}/ &&
            rm -rf tmprepo
            """
        ]
        def proc = cloneCmd.execute()
        proc.waitFor()
        println "[INFO] Git repo cloned into ${BASE_DIR}"
    } else {
        println "[INFO] CLI already present at ${CLI_FILE}"
    }
} catch (Exception e) {
    return ["Failed cloning repo: ${e.message}"]
}

try {
    def depCmd = ["bash","-c","cd ${BASE_DIR} && ${PYTHON} -m pip install --user --quiet -r ${REQ_FILE} || true"]
    def depProc = depCmd.execute()
    depProc.waitFor()
    println "[INFO] Dependencies installed (if needed)."
} catch (Exception e) {
    println "[WARN] Could not install dependencies: ${e.message}"
}

def cred = CredentialsProvider.lookupCredentials(
    StandardFileCredentials.class,
    Jenkins.instance,
    null,
    null
).find { it.id == CRED_ID }

if (!cred) {
    return ["Jenkins credential '${CRED_ID}' not found."]
}

// Write temp credential JSON
def tmpCredFile = File.createTempFile("motio-cred-", ".json")
tmpCredFile.deleteOnExit()
tmpCredFile.bytes = cred.content

try {

    def loginCmd = [
        "bash","-c",
        """
        cd ${BASE_DIR} &&
        ${PYTHON} ci-cli.py --server=${MOTIO_SERVER} \
          login --credentialsFile ${tmpCredFile.absolutePath} > login.out 2>&1 &&
        awk 'match(\$0,/(xauthtoken|Auth[[:space:]]*Token)[[:space:]]*[:=][[:space:]]*([A-Za-z0-9._-]+)/,m){print m[2]}' login.out | tail -n1
        """
    ]
    def loginProc = loginCmd.execute()
    def loginOut = new StringBuffer()
    loginProc.consumeProcessOutput(loginOut,new StringBuffer())
    loginProc.waitFor()
    TOKEN = loginOut.toString().trim()

    if (!TOKEN || TOKEN.length() < 10)
        return ["Failed to obtain MotioCI token — verify credentials.json or CLI path"]


    def listCmd = [
        "bash","-c",
        """
        cd ${BASE_DIR} &&
        ${PYTHON} ci-cli.py --server=${MOTIO_SERVER} \
          project ls --xauthtoken ${TOKEN} \
          --instanceName ${SOURCE_ENV}
        """
    ]
    def listProc = listCmd.execute()
    def listOut = new StringBuffer()
    def listErr = new StringBuffer()
    listProc.consumeProcessOutput(listOut, listErr)
    listProc.waitFor()

    def raw = listOut.toString().trim()
    if (!raw) {
        println "[ERROR] STDERR: ${listErr}"
        return ["No response from MotioCI — check server/network."]
    }


    def json = new JsonSlurper().parseText(raw)
    def edges = json?.data?.instances?.edges ?: []
    edges.each { e ->
        e?.node?.projects?.edges?.each { p ->
            def name = p?.node?.name
            if (name) projects << name
        }
    }

    return projects.isEmpty() ? ["No projects found for ${SOURCE_ENV}"] : projects.unique().sort()

} catch (Exception e) {
    return ["Error fetching projects: ${e.message}"]
} finally {
    tmpCredFile.delete()
}
