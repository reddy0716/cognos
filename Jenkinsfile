if [[ -n \$(git status --porcelain -- "tar-surge-client/SurgeUpdate_\$TO_ENV_UPPER.ZIP" "tar-surge-client/SurgeInstall_\$TO_ENV_UPPER.bat") ]]; then
  git add "tar-surge-client/SurgeUpdate_\$TO_ENV_UPPER.ZIP" \\
          "tar-surge-client/SurgeInstall_\$TO_ENV_UPPER.bat"
  git commit -m "Automated SurgeUpdate promotion to \$TO_ENV_UPPER"
  git push origin master
fi
