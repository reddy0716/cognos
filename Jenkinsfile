# --------------------------------------------------------------------
# Fetch MotioCI Projects & Folders for Dynamic Parameters
# --------------------------------------------------------------------
echo "Fetching available projects for ${SOURCE_ENV}..."
python3 ci-cli.py --server="$MOTIO_SERVER" \
  project ls --xauthtoken "$TOKEN" \
  --instanceName "${SOURCE_ENV}" > ../projects_raw.json

# Extract project names and save to workspace
grep -o "'name': '[^']*'" ../projects_raw.json | cut -d"'" -f4 | sort | uniq > ../../projects.txt || true
echo "Saved project list to: $WORKSPACE/projects.txt"
head -n 10 ../../projects.txt || echo "(No projects found)"

# If user already selected a project, fetch its folders
if [ -n "${PROJECT_NAME:-}" ]; then
  echo "Fetching folders for project: ${PROJECT_NAME}"
  python3 ci-cli.py --server="$MOTIO_SERVER" \
    versionedItems ls --xauthtoken "$TOKEN" \
    --instanceName "${SOURCE_ENV}" \
    --projectName "${PROJECT_NAME}" --currentOnly True > ../folders_raw.json || true

  grep "prettyPath" ../folders_raw.json | cut -d"'" -f4 | sort | uniq > ../../folders.txt || true
  echo "Saved folder list to: $WORKSPACE/folders.txt"
  head -n 10 ../../folders.txt || echo "(No folders found)"
else
  echo "Skipping folder discovery — PROJECT_NAME not provided yet."
fi

# Double-check both files exist
ls -lh ../../projects.txt ../../folders.txt || true
