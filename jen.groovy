echo 'Listing instances with provided token...'
  OUT=\\"$(python3 ci-cli.py --server=\\"https://cgrptmcip01.cloud.cammis.ca.gov\\" \
           instance ls --xauthtoken \\"${MOTIO_AUTH_TOKEN}\\")\\"
  echo \\"$OUT\\" | sed -e 's/^/  /'
