import groovy.json.JsonSlurper

def MOTIO_SERVER = "https://cgrptmcip01.cloud.cammis.ca.gov"
def SOURCE_ENV = SOURCE_ENV ?: "Cognos-DEV/TEST"
def PROJECT_NAME = PROJECT_NAME ?: ""

def folders = ["Deploy Whole Project"]

try {
    if (PROJECT_NAME) {
        def cmd = """
          python3 /var/lib/jenkins/workspace/MotioCI/api/CLI/ci-cli.py \
            --server=${MOTIO_SERVER} versionedItems ls \
            --instanceName ${SOURCE_ENV} \
            --projectName ${PROJECT_NAME} \
            --currentOnly True
        """
        def output = cmd.execute().text
        def lines = output.readLines().findAll { it.contains("/Team Content") }
        lines.each {
            def path = it.split("'")[3]
            folders << path
        }
    }
    return folders.unique().sort()
} catch (Exception e) {
    return ["Deploy Whole Project", "Error fetching folders: ${e.message}"]
}
