after reviewing IBM docs, I believe we can use the official REST flow for a reliable headless login. Could you please review and confirm the specifics below for our tenant?

1) API base host & routing for REST
   Please confirm the exact PRD hostname/path we should call for REST (dispatcher vs. any reverse proxy that properly maps /api to the backend). If a gateway is involved, we want to ensure REST endpoints and cookies are issued correctly through intermediaries.
   Doc: https://www.ibm.com/docs/en/cognos-analytics/12.0.x?topic=settings-customizing-cookie

2) Auth flow via /api/v1/session (non-interactive)
   We intend to use the documented flow: start a session and then authenticate by sending a PUT to /api/v1/session with a case-sensitive parameters[] payload. If our AzureAD namespace disallows username/password, please advise whether CAMAPILoginKey is supported and the exact parameter name/value to send.
   Docs:
   - https://www.ibm.com/docs/en/cognos-analytics/12.0.x?topic=api-getting-started-rest
   - https://www.ibm.com/docs/en/cognos-analytics/11.2.x?topic=api-rest-sample

3) XSRF requirement & header
   After session creation, we understand we must include X-XSRF-Token on subsequent requests (value from the XSRF cookie returned at session start). Please confirm this sequence for our tenant.
   Doc: https://www.ibm.com/docs/en/cognos-analytics/12.0.x?topic=api-getting-started-rest

4) Token to carry downstream (session_key vs. cookie)
   Preferred: use the returned session_key with header IBM-BA-Authorization: CAM <session_key> for headless calls. If our policy requires a cam_passport cookie instead, please confirm how to obtain it programmatically (or if there’s an approved exchange from session_key to cam_passport that MotioCI can consume).
   Doc: https://www.ibm.com/docs/en/cognos-analytics/12.0.x?topic=api-getting-started-rest

5) Cookie behavior through proxies (only if cookie-based auth is required)
   If we must rely on cookies, please confirm cookie domain/path/secure settings are correct when traversing proxies/LB so Cognos can set cookies to our client.
   Doc: https://www.ibm.com/docs/en/cognos-analytics/12.0.x?topic=settings-customizing-cookie

6) Minimal tenant-specific cURL example
   Please provide a short cURL sequence that:
   (a) starts the session (GET /api/v1/session),
   (b) authenticates (PUT /api/v1/session with our provider’s parameters[]),
   (c) shows either the returned session_key (preferred) or that a cam_passport is set, including any XSRF header required.
   Doc: https://www.ibm.com/docs/en/cognos-analytics/11.2.x?topic=api-rest-sample

7) Session/idle timeout policy
   Please share the configured session and idle timeouts so we mint a fresh token each run (and understand expiry behavior of CAMPassport).
   Doc: https://www.ibm.com/docs/en/cognos-analytics/12.0.x?topic=classes-campassport

Once we have the confirmed endpoint, payload, and token to carry, I’ll swap our pipeline to the official flow and remove the temporary manual-cookie workaround.
