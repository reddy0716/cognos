                  # ADDED: Git LFS inline install + track large ZIPs
                  # ─────────────────────────────────────────────────────────
                  if ! command -v git-lfs >/dev/null 2>&1; then
                    echo "git-lfs not found, installing inline..."
                    mkdir -p \$HOME/bin
                    curl -sSL --cacert /etc/pki/tls/certs/ca-bundle.crt \\
                      https://github.com/git-lfs/git-lfs/releases/download/v3.7.1/git-lfs-linux-amd64-v3.7.1.tar.gz \\
                      -o /tmp/git-lfs.tar.gz
                    tar -xzf /tmp/git-lfs.tar.gz -C /tmp
                    cp /tmp/git-lfs-3.7.1/git-lfs \$HOME/bin/git-lfs
                    chmod +x \$HOME/bin/git-lfs
                    export PATH="\$HOME/bin:\$PATH"
                  fi
                  git-lfs install
                  git lfs track "*.ZIP"
                  git add .gitattributes
                  # ─────────────────────────────────────────────────────────
