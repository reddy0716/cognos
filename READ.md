# DEV and SANDBOX Co‑Existence on a Single IIS Server

## 1. Purpose of This Document

This document explains **how DEV and SANDBOX environments are safely hosted on the same IIS server** without conflicts. It details:

* Deployment flow
* Drive usage
* IIS site & app pool separation
* Jenkins and CodeDeploy responsibilities
* Environment variable isolation
* Why there are **no clashes** between DEV and SANDBOX

This document can be used for **architecture review, developer clarification, audits, and leadership sign‑off**.

---

## 2. High‑Level Architecture Overview

| Layer                | Responsibility                                   |
| -------------------- | ------------------------------------------------ |
| Jenkins (Kubernetes) | Build, token replacement, trigger CodeDeploy     |
| AWS CodeDeploy       | Artifact delivery + execution control on Windows |
| IIS (Windows Server) | Hosting .NET applications                        |
| Vault                | Secrets management                               |

**Key principle:**

> *Build happens in Jenkins. Server state changes happen only through CodeDeploy.*

---

## 3. Environment Separation Strategy

To avoid conflicts, DEV and SANDBOX are isolated across **four layers**:

1. **Disk (Drive‑level separation)**
2. **IIS (Site + App Pool isolation)**
3. **Environment variables (App‑pool scoped)**
4. **CodeDeploy applications & pipelines**

This ensures both environments can run **simultaneously** on the same server.

---

## 4. Drive Usage (No File Conflicts)

### DEV (Existing – D Drive)

```
D:\
├── inetpub\Apiservices            (Live IIS site)
├── tar-surge-Api-staging\         (CodeDeploy staging)
├── IISLogs
└── apps\ErrorLogs
```

### SANDBOX (New – E Drive)

```
E:\
├── inetpub\Apiservices-SBX        (Live IIS site)
├── tar-surge-Api-staging-sbx\     (CodeDeploy staging)
├── IISLogs-SBX
└── apps\ErrorLogs-SBX
```

**Result:**

* No shared folders
* No overwrite risk
* Clear ownership per environment

---

## 5. IIS Isolation (Critical for Stability)

Each environment has its **own IIS identity**.

| Component     | DEV                    | SANDBOX                    |
| ------------- | ---------------------- | -------------------------- |
| IIS Site Name | Apiservices            | Apiservices-SBX            |
| IIS App Pool  | Apiservices            | Apiservices-SBX            |
| Physical Path | D:\inetpub\Apiservices | E:\inetpub\Apiservices-SBX |
| Logs          | D:\IISLogs             | E:\IISLogs-SBX             |

**No IIS components are shared.**

---

## 6. Environment Variables (No Clashes)

### Why Machine‑Level Variables Are Unsafe

Machine‑level environment variables apply to **all apps on the server**. Using them would cause:

* DEV and SANDBOX overwriting each other
* Last deployment winning
* Silent production‑like failures

### Correct Approach: App Pool–Scoped Variables

#### DEV App Pool

```powershell
Set-ItemProperty IIS:\AppPools\Apiservices \
  -Name processModel.environmentVariables \
  -Value @{
    SURGE_ENVNAME = "DEV"
    SURGE_RPM_ROOT = "D:\inetpub\ApiServices\RPM\dhcs_dev\rpm_root"
  }
```

#### SANDBOX App Pool

```powershell
Set-ItemProperty IIS:\AppPools\Apiservices-SBX \
  -Name processModel.environmentVariables \
  -Value @{
    SURGE_ENVNAME = "SANDBOX"
    SURGE_RPM_ROOT = "E:\inetpub\ApiServices-SBX\RPM\sandbox\rpm_root"
  }
```

**Result:**

* Variables are isolated per app
* No cross‑environment impact
* IIS restart affects only that app pool

---

## 7. Jenkins Pipelines and Their Responsibilities

### 7.1 Config Pipeline (`jenkisfile.config`)

**Runs once per environment**

Responsibilities:

* Create IIS Site
* Create App Pool
* Configure bindings, recycle schedule, logging
* Grant cert permissions
* Perform IIS reset

This pipeline is used for:

* First‑time setup
* Structural changes only

---

### 7.2 Environment Pipeline (`jenkinsfile.env`)

**Used when secrets or env values change**

Responsibilities:

* Inject Vault paths and credentials
* Set app‑pool scoped environment variables
* Restart IIS (for env vars to take effect)

No application code is deployed.

---

### 7.3 Application Pipeline (`Jenkinsfile`)

**Runs for every application deployment**

Responsibilities:

* Build .NET application
* Publish artifacts
* Copy files into IIS physical path
* Restart only site and app pool

No IIS structure or env vars are modified.

---

## 8. CodeDeploy Execution Logic (Safety Mechanism)

The `after-install.bat` script acts as a **router**:

```bat
if DEPLOY_ENVIRONMENT == true → deploy-environment.ps1
if DEPLOY_CONFIG == true      → deploy-config.ps1
if DEPLOY_FILES == true       → deploy-files.ps1
```

Only **one mode runs per deployment**, preventing accidental overlaps.

---

## 9. Deployment Order (Why It Works)

Correct and proven order:

1. **Config pipeline** – create IIS structure
2. **Env pipeline** – set secrets & env vars
3. **Application pipeline** – deploy code

This order ensures:

* IIS exists before files are deployed
* Env vars are available before app starts
* App deploys are fast and repeatable

---

## 10. Why There Are No Clashes

| Risk Area          | Mitigation                 |
| ------------------ | -------------------------- |
| File overwrite     | Separate drives (D: / E:)  |
| IIS conflicts      | Separate sites & app pools |
| Env var collision  | App‑pool scoped vars       |
| Deployment overlap | CodeDeploy routing flags   |
| Concurrent deploys | Jenkins locks              |

---

## 11. Final Conclusion

✔ DEV and SANDBOX can safely run on the **same IIS server**
✔ Drive‑level isolation prevents file conflicts
✔ IIS app pool isolation prevents runtime clashes
✔ App‑scoped environment variables prevent configuration leaks
✔ Existing Jenkins + CodeDeploy design fully supports this model

**No architectural changes are required — only controlled extensions.**

---

## 12. One‑Line Summary for Stakeholders

> “DEV and SANDBOX are isolated by drive, IIS site, app pool, and environment scope, allowing both to coexist safely on the same server without conflicts.”

---

**End of Document**
