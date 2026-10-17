"use strict";
const test=require("node:test"), assert=require("node:assert/strict");
const {references,requireUnpaid}=require("./entry-deletion-policy");
const job={totalAmount:100,amountReceived:0,pendingAmount:100};
test("unpaid and reversed split history are safe and unchanged",()=>{const c={status:"REVERSED",jobEntryId:9n,allocationsJson:'[{"jobId":1,"amount":40}]'};requireUnpaid(job,[c],"1");assert.equal(c.status,"REVERSED");});
test("active split allocation blocks source deletion",()=>assert.throws(()=>requireUnpaid(job,[{status:"ACTIVE",jobEntryId:9n,allocationsJson:'[{"jobId":1,"amount":40}]'}],"1")));
test("paid and inconsistent balances block deletion",()=>{for(const paid of [40,100]) assert.throws(()=>requireUnpaid({...job,amountReceived:paid,pendingAmount:100-paid},[],"1"));assert.throws(()=>requireUnpaid({...job,pendingAmount:1},[],"1"));});
test("63-bit split identity does not lose precision",()=>assert.equal(references({jobEntryId:2n,allocationsJson:'[{"jobId":9223372036854775807,"amount":40}]'},"9223372036854775807"),true));
test("malformed allocation history blocks reconciliation",()=>assert.throws(()=>requireUnpaid(job,[{jobEntryId:9n,allocationsJson:'broken'}],"1")));
