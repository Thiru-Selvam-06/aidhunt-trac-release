// Connectivity fixture for disposable GitHub Android emulators, never a backend.
const {createServer}=require('node:http');
function requireSyntheticCI(env=process.env){
 if(env.CI!=='true'||env.GITHUB_ACTIONS!=='true'||env.GCLOUD_PROJECT!=='demo-aidhunt-v16'||env.GOOGLE_APPLICATION_CREDENTIALS)
  throw Error('REFUSED: disposable GitHub emulator and demo project required');
}
function createConnectivityFixture(){return createServer((req,res)=>{
 if(!['GET','HEAD'].includes(req.method)){res.writeHead(405);res.end();return;}
 res.writeHead(req.url==='/generate_204'?204:404,{'Cache-Control':'no-store'});res.end();
});}
if(require.main===module){requireSyntheticCI();const server=createConnectivityFixture();
 server.listen(13080,'127.0.0.1',()=>console.log('Synthetic connectivity fixture listening on loopback:13080'));
 for(const signal of ['SIGTERM','SIGINT'])process.on(signal,()=>server.close(()=>process.exit(0)));
}
module.exports={requireSyntheticCI,createConnectivityFixture};
