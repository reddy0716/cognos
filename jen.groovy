Hi [Manager’s Name],

While testing the Cognos PRD environment manually through the import wizard, I was prompted for an encryption password. This is not the AD service account password—it is a separate Cognos configuration password that is set up during Cognos installation and is required for any import/export of deployment archives.

At this point, my AD service account allows me to log into PRD successfully, but without the encryption password I cannot complete the import process. The same password will also be required when running deployments through MotioCI, otherwise the jobs will fail with “Access is denied” errors.

Next steps:

We will need to coordinate with the Cognos administrators to either obtain the encryption password or have them configure it for use in PRD deployments.

Once this is addressed, we can complete the automated deployment pipeline without manual intervention.

Please let me know how you would like me to proceed with the Cognos admin team.

Thanks,
