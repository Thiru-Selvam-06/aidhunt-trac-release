from pathlib import Path
import json,os,subprocess,sys,time,shutil
root=Path(__file__).resolve().parents[1]
out=root/'.codex/validation-v16';out.mkdir(parents=True,exist_ok=True)
env=dict(os.environ,FIRESTORE_EMULATOR_HOST='127.0.0.1:18180',GCLOUD_PROJECT='demo-aidhunt-v16')
node=shutil.which('node.exe') or shutil.which('node')
npm=shutil.which('npm.cmd') or shutil.which('npm')
if not node or not npm:raise SystemExit('Node/npm are required; validation did not run')
phases=[('dependencies-rules',[npm,'ci','--prefix','security-tests']),('dependencies-functions',[npm,'ci','--prefix','functions']),('android',[str(root/'gradlew.bat'),'testDebugUnitTest','--rerun','assembleDebug','lintDebug','--no-daemon','--no-configuration-cache']),('rules',[node,'--test','security-tests/architecture.test.cjs']),('services',[node,'--test','functions/security.integration.test.js']),('invitation',[node,'--test','functions/invitation-policy.test.js'])]
results={}
for name,command in phases:
 start=time.monotonic()
 with (out/(name+'.log')).open('wb') as log:
  process=subprocess.run(command,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
 results[name]={'exit':process.returncode,'seconds':round(time.monotonic()-start,2)}
 print(name+': exit '+str(process.returncode),flush=True)
 (out/'results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
 # Fail closed: do not advance past a failed prerequisite/check or report success.
 if process.returncode:raise SystemExit(process.returncode)
print('ALL CONSOLIDATED CHECKS PASSED',flush=True)
