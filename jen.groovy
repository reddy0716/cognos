stage('Debug Namespaces') {
  steps {
    container('python') {
      sh '''
        echo "Checking available namespaces for PRD instance..."
        curl -sk -X POST "https://cgrptmcip01.cloud.cammis.ca.gov/api/graphql" \
          -H "Content-Type: application/json" \
          -H "x-auth-token: ${TOKEN}" \
          -d '{
            "query":"query($id:Int!){ instance(id:$id){ namespaces { id name } } }",
            "variables":{"id":1}
          }' | jq .
      '''
    }
  }
}
