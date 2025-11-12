import groovy.json.JsonSlurper

def MOTIO_SERVER = "https://cgrptmcip01.cloud.cammis.ca.gov"
def SOURCE_ENV = SOURCE_ENV ?: "Cognos-DEV/TEST"

def projects = []
try {
    def cmd = """
      python3 /var/lib/jenkins/workspace/MotioCI/api/CLI/ci-cli.py \
        --server=${MOTIO_SERVER} project ls \
        --instanceName ${SOURCE_ENV}
    """
    def output = cmd.execute().text
    def lines = output.readLines().findAll { it.contains("'name':") }
    lines.each {
        def name = it.split("'")[3]
        projects << name
    }
    return projects.unique().sort()
} catch (Exception e) {
    return ["Failed to fetch projects: ${e.message}"]
}
