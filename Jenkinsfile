def getDynamicFolders() {
  try {
    def MOTIO_SERVER = "https://cgrptmcip01.cloud.cammis.ca.gov"
    def SOURCE_ENV = "Cognos-DEV/TEST"
    def PROJECT_NAME = "Demo"
    def folderList = ["Deploy Whole Project"]

    // Run the MotioCI CLI via Python to get live folder paths
    def output = sh(script: """
      cd MotioCI/api/CLI || exit 0
      python3 ci-cli.py --server="${MOTIO_SERVER}" versionedItems ls \
        --instanceName "${SOURCE_ENV}" \
        --projectName "${PROJECT_NAME}" --currentOnly True | grep "searchPath" | grep "/Team Content" | awk -F"'" '{print \$4}' | sort -u
    """, returnStdout: true).trim()

    if (output) {
      folderList.addAll(output.split("\\n"))
    } else {
      folderList.add("/Team Content/MotioCI Reports/Default Folder")
    }

    return folderList.unique()
  } catch (e) {
    echo "⚠️ Dynamic folder retrieval failed: ${e.getMessage()}"
    return ["Deploy Whole Project"]
  }
}
