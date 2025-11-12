import groovy.json.JsonSlurper

// ===== CONFIG =====
def MOTIO_SERVER = "https://cgrptmcip01.cloud.cammis.ca.gov"
def SOURCE_ENV = SOURCE_ENV ?: "Cognos-DEV/TEST"
def WORKSPACE = "/var/lib/jenkins/workspace/MotioCI/api/CLI"
def TOKEN_FILE = "/var/lib/jenkins/workspace/MotioCI/token.txt"
// ===================

def projects = []
def TOKEN = ""

try {

    def tokenFile = new File(TOKEN_FILE)
    if (!tokenFile.exists()) {
        return ["MotioCI token not found — please run a pipeline build once to generate token.txt"]
    }

    TOKEN = tokenFile.text.trim()
    if (!TOKEN || TOKEN.length() < 10) {
        return ["Invalid or empty token — rerun MotioCI Login stage"]
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

    def proc = listCmd.execute()
    def output = new StringBuffer()
    proc.consumeProcessOutput(output, new StringBuffer())
    proc.waitFor()

    def raw = output.toString().trim()
    if (!raw) {
        return ["No response from MotioCI — check server/network"]
    }


    def data = new JsonSlurper().parseText(raw)
    def edges = data?.data?.instances?.edges ?: []
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
