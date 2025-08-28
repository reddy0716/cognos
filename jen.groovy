logs:
  - type: file
    path: "D:\\inetpub\\ApiServices\\surgelogs\\apilogger*"
    service: "apilogger"
    source: "TAR-SURGE-APP"

  - type: file
    path: "D:\\inetpub\\ApiServices\\surgelogs\\apiserver*"
    service: "apiserver"
    source: "TAR-SURGE-APP"

  - type: file
    path: "D:\\inetpub\\ApiServices\\surgelogs\\mfOutboundlogger*"
    service: "mfOutboundlogger"
    source: "TAR-SURGE-APP"
