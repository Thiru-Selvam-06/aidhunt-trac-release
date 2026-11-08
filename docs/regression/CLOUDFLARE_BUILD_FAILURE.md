# Cloudflare automatic staging build — confirmed configuration failure

Read-only dashboard inspection: 8 November 2026, after user sign-in.
Worker: `aidhunt-trac-trusted-staging`.
Build: `fba1ad1e-6c53-4b97-b272-82e4c7dcff65`.
Source: core development commit `ae16cdf85949d93357cdaf0b12b505240377c569`.

## Actual first failing step

Initialization and cloning passed. The dashboard specifies no build command,
repository root `/`, and deploy command `npx wrangler preview`. That command
downloaded Wrangler4.158.0 and failed with:

> Your Wrangler configuration is missing a `previews` block to run this command.

The build ended with an error in the deploy command. No successful preview upload
or deployment is shown for this build. The pre-existing Cloudflare integration
attempted this automatically after a development-branch push; no Retry build or
deploy action was clicked during this investigation.

This is separate from the repository's mandatory GitHub `Staging Worker build only
(no deployment)` job, which passed at the same source. That job installs the
existing lockfile and runs `npm run build` from `cloudflare/`. The project uses
`cf build`, Vite and `cloudflare/cloudflare.config.ts`; no Wrangler configuration
is present in the tracked source.

## Safe correction and release boundary

Do not add an empty previews block solely to make the red check disappear. That
would move execution beyond the current failed deploy step without establishing
data isolation. The existing staging configuration binds `FIREBASE_PROJECT_ID`
to the LIVE project `trac-aidhunt`, with a specific staging workspace. A staging
name is not proof of an isolated Firebase project.

Reuse the existing build-only GitHub gate now. Before enabling automatic previews:

1. Review which branches the Cloudflare integration may publish and which existing
   deployment/service it affects. Keep production branch deployment separately gated.
2. Select a genuinely isolated demo Firebase/Auth target and synthetic workspace;
   do not copy the live privileged credential into a disposable preview.
3. Match the existing programmatic/Vite build setup and correct working directory,
   or review an explicit Wrangler preview configuration as a coordinated tooling
   change. Do not silently introduce a second competing deployment configuration.
4. Validate the chosen configuration with local build/backend tests first. Prepare
   exact dashboard changes for user approval before applying or retrying deployment.
5. Verify the actual preview project/workspace and authorization using synthetic
   accounts before permitting any APK to use it.

Cloudflare documents that `wrangler preview` requires a `previews` block:
[Preview configuration](https://developers.cloudflare.com/workers/previews/configuration/).
This explains the observed command failure; it does not authorize a deployment.

No dashboard settings, access permissions, secrets, customer records or Worker
deployment were changed. This external integration remains a known failed check.
