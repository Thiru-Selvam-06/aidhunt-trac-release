const test=require('node:test'),assert=require('node:assert/strict');
const {requireSyntheticCI,createConnectivityFixture}=require('./device-network-fixture.cjs');
test('connectivity fixture refuses non-CI, non-GitHub, live project and credentials',()=>{
 const valid={CI:'true',GITHUB_ACTIONS:'true',GCLOUD_PROJECT:'demo-aidhunt-v16'};
 assert.doesNotThrow(()=>requireSyntheticCI(valid));
 for(const bad of [{CI:'false'},{GITHUB_ACTIONS:'false'},{GCLOUD_PROJECT:'trac-aidhunt'},{GOOGLE_APPLICATION_CREDENTIALS:'never-read.json'}])
  assert.throws(()=>requireSyntheticCI({...valid,...bad}),/REFUSED/);
});
test('loopback fixture supplies only the synthetic connectivity response',async()=>{
 const server=createConnectivityFixture();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{assert.equal(server.address().address,'127.0.0.1');const url=`http://127.0.0.1:${server.address().port}`;
  for(const method of ['GET','HEAD']){const r=await fetch(url+'/generate_204',{method});assert.equal(r.status,204);assert.equal(await r.text(),'');}
  assert.equal((await fetch(url+'/unknown')).status,404);
  assert.equal((await fetch(url+'/generate_204',{method:'POST'})).status,405);
 }finally{await new Promise(resolve=>server.close(resolve));}
});
