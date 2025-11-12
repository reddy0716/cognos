import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*

def store = Jenkins.instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
def allCreds = store.getCredentials(Domain.global())

println "===== Jenkins Global Credentials ====="
allCreds.each { c ->
    println "ID: ${c.id} | Type: ${c.class.simpleName}"
}
return
