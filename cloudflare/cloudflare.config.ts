import { bindings, defineConfig } from 'cf/config';

export default defineConfig({
  accountId: '70d29191fa1fbf347c9d64b9b6691947',
  worker: {
    name: 'aidhunt-trac-trusted-staging',
    entrypoint: './worker.mjs',
    compatibilityDate: '2026-10-13',
    env: {
      FIREBASE_PROJECT_ID: bindings.text('trac-aidhunt'),
      STAGING_WORKSPACE_ID: bindings.text('ws_cf_staging_20261015_login_a62e71'),
      GOOGLE_SERVICE_ACCOUNT_JSON: bindings.secret(),
    },
  },
});
