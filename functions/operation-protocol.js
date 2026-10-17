"use strict";
const {HttpsError}=require('firebase-functions/v2/https');
const VERSION=2;
function validate(data,uid,type){
 if(!data || data.protocolVersion!==VERSION || !Number.isSafeInteger(data.actionTime) || data.actionTime<=0 || !data.operationId || !data.workspaceId || !data.actorContext || !data.operationType || !Array.isArray(data.dependencies))
  throw new HttpsError('failed-precondition','Update AIDHUNT Trac and review retained pending actions; original timestamps must not be recreated.',{code:'UPGRADE_REQUIRED',requiredProtocolVersion:VERSION});
 if(data.actorContext!==uid)throw new HttpsError('permission-denied','Authenticated actor does not match operation context');
 if(data.operationType!==type || data.actionTime>Date.now() || !/^[A-Za-z0-9-]{1,128}$/.test(data.operationId) || data.dependencies.length>100 || data.dependencies.some(d=>!d || !['JOB','CUSTOMER','EXPENSE','PAYMENT','TRACTOR','EXTENSION'].includes(d.entityType) || !/^[1-9][0-9]{0,18}$/.test(d.entityId) || BigInt(d.entityId)>9223372036854775807n))throw new HttpsError('invalid-argument','Invalid operation protocol fields');
}
module.exports={VERSION,validate};
