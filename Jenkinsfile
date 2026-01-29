def ENV_ALIAS_MAP = [
  "SBX": ["TARGET": "DEV", "DISPLAY": "SANDBOX"],
  "HFX": ["TARGET": "SIT", "DISPLAY": "HOTFIX"]
]

if (params.DEPLOY_ENV == "NONE") {
  TARGET_ENV  = "NONE"
  DISPLAY_ENV = "NONE"
} else {
  TARGET_ENV  = ENV_ALIAS_MAP[params.DEPLOY_ENV]["TARGET"]
  DISPLAY_ENV = ENV_ALIAS_MAP[params.DEPLOY_ENV]["DISPLAY"]
}

echo "User selected label   : ${params.DEPLOY_ENV}"
echo "Target server env     : ${TARGET_ENV}"
echo "Displayed environment : ${DISPLAY_ENV}"
