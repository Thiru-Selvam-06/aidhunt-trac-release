import {spawnSync} from 'node:child_process';
import {mkdirSync, writeFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const groups={
 backend:['cloudflare/operations.test.mjs','cloudflare/google-auth.test.mjs','cloudflare/firestore-rest.test.mjs',
 'functions/invitation-policy.test.js','functions/entry-deletion-policy.test.js',
 'functions/entry-deletion-http.test.js','functions/migration/legacy-timestamps.test.js'],
 rules:['security-tests/architecture.test.cjs','security-tests/listener-queries.test.cjs',
 'security-tests/phone-onboarding.test.cjs','security-tests/workspace-discovery.test.cjs',
 'security-tests/purge-protocol.test.cjs','functions/security.integration.test.js',
 'functions/entry-deletion-emulator.test.js']
};
const suite=process.argv[2], files=groups[suite];
if(!files) throw new Error('Choose backend or rules');
if(process.argv.includes('--list')) { console.log(JSON.stringify(files)); process.exit(0); }
if(process.env.GCLOUD_PROJECT!=='demo-aidhunt-v16' ||
 process.env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:18180' ||
 process.env.GOOGLE_APPLICATION_CREDENTIALS) {
 console.error('REFUSED: require demo-aidhunt-v16 and local Firestore emulator, without Google credential files');
 process.exit(2);
}
mkdirSync(resolve(root,'ci-reports'),{recursive:true});
const prefix=resolve(root,'ci-reports',suite);
const args=['--test','--test-concurrency=1','--test-reporter=spec','--test-reporter-destination=stdout',
 '--test-reporter=junit','--test-reporter-destination='+prefix+'.xml',...files];
const started=new Date();
const result=spawnSync(process.execPath,args,{cwd:root,stdio:'inherit',env:process.env});
writeFileSync(prefix+'-run.json',JSON.stringify({suite,project:'demo-aidhunt-v16',
 sha:process.env.GITHUB_SHA??null,runId:process.env.GITHUB_RUN_ID??null,node:process.version,
 started:started.toISOString(),ended:new Date().toISOString(),exit:result.status,
 signal:result.signal,files},null,2));
if(result.error) console.error(result.error.code);
process.exit(result.status??1);
