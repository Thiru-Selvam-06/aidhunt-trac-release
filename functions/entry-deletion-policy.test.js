"use strict";
const test=require("node:test"), assert=require("node:assert/strict");
const {references,requireUnpaid}=require("./entry-deletion-policy");
const {customerTotals}=require("./entry-deletion-policy");
const {plannedCollectionDeletes}=require("./entry-deletion-policy");
const job={totalAmount:100,amountReceived:0,pendingAmount:100};
test("unpaid and reversed split history are safe and unchanged",()=>{const c={status:"REVERSED",jobEntryId:9n,allocationsJson:'[{"jobId":1,"amount":40}]'};requireUnpaid(job,[c],"1");assert.equal(c.status,"REVERSED");});
test("active split allocation blocks source deletion",()=>assert.throws(()=>requireUnpaid(job,[{status:"ACTIVE",jobEntryId:9n,allocationsJson:'[{"jobId":1,"amount":40}]'}],"1")));
test("paid and inconsistent balances block deletion",()=>{for(const paid of [40,100]) assert.throws(()=>requireUnpaid({...job,amountReceived:paid,pendingAmount:100-paid},[],"1"));assert.throws(()=>requireUnpaid({...job,pendingAmount:1},[],"1"));});
test("63-bit split identity does not lose precision",()=>assert.equal(references({jobEntryId:2n,allocationsJson:'[{"jobId":9223372036854775807,"amount":40}]'},"9223372036854775807"),true));
test("malformed allocation history blocks reconciliation",()=>assert.throws(()=>requireUnpaid(job,[{jobEntryId:9n,allocationsJson:'broken'}],"1")));

test('explicit Owner deletion may remove a reconciled legacy embedded receipt',()=>{
 const source={workspaceId:'a',customerId:2n,totalAmount:2500,amountReceived:2500,pendingAmount:0};
 assert.deepEqual(plannedCollectionDeletes(source,[],'1','OWNER','owner',Date.now()),[]);
 for(const role of ['PARTNER','OPERATOR'])
  assert.throws(()=>plannedCollectionDeletes(source,[],'1',role,'owner',Date.now()));
 for(const extra of [{protocolVersion:2},{authorizationTimeStatus:'CLIENT_ORIGINAL_V2'},{collectionId:'55'},{pendingAmount:1},{amountReceived:2501}])
  assert.throws(()=>plannedCollectionDeletes({...source,...extra},[],'1','OWNER','owner',Date.now()));
});

test("dues ignore stale pending cache and reconcile displayed rounding",()=>{
 assert.deepEqual(customerTotals([{totalAmount:100.4,amountReceived:40.5,pendingAmount:1}]),
  {totalBilled:100,totalPaid:41,balanceDue:59});
});
test("orphan legacy payment rows never create a zero-paid customer ledger",()=>{
 assert.deepEqual(customerTotals([{workType:'Payment Received',totalAmount:40,amountReceived:40,pendingAmount:0}]),
  {totalBilled:0,totalPaid:0,balanceDue:0});
});
