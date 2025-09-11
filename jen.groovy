query getInstanceAndNamespaceInformation {
  instances {
    edges {
      node {
        id
        name
        namespaces { id }
      }
    }
  }
}
