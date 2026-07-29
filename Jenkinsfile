Subject: Sandbox & Hotfix Environment Setup on Dev/SIT Servers – Summary

Hi [Manager's Name],

As requested, here's a summary of the work completed to stand up the Sandbox and Hotfix environments alongside our existing Dev and SIT environments on the same servers.

**Objective**
Set up Sandbox and Hotfix environments co-located on the same servers as Dev and SIT, without disrupting existing Dev/SIT functionality.

**Artifact Storage**
- Created a separate folder on the IIS server's E drive to hold all Sandbox and Hotfix artifacts, keeping them cleanly separated from the existing D drive used by Dev and SIT.

**IIS Configuration (Sites & App Pools)**
- No config-level dependencies were involved — the main change was updating paths and creating new IIS sites and app pools with distinct names for Sandbox and Hotfix, so they run independently from Dev and SIT on the same server.

**Environment Variables**
- Dev and SIT currently have env variables set at the machine level, which creates a dependency/conflict risk for any new environment added to the same box.
- To avoid this, Sandbox and Hotfix env variables were configured at the app pool level instead. This isolates each environment's variables to its own app pool without touching or affecting the machine-level settings used by Dev/SIT.

**Build & Deployment Pipeline**
- Application build process mirrors Dev/SIT.
- Sandbox: a new branch (sandbox00) triggers the build, and artifacts are automatically deployed to the Sandbox path on the IIS server's E drive.
- Hotfix: uses a promotion pipeline that promotes everything from Sandbox to Hotfix — config, env variables, artifacts, sites, and app pools — following the same pattern as Sandbox.

**Testing Status**
- Sandbox has been tested end-to-end and is working as expected.
- Hotfix is built the same way and is expected to behave identically; final validation is [in progress / pending — let me know which applies].

You're welcome to verify this setup directly on the Dev and SIT servers — everything is live and configured as described above.

Branch reference: [insert Git branch name/link here]

Happy to walk through any part of this in more detail.

Thanks,
[Your Name]
