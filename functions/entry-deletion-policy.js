"use strict";
function references(collection, id) {
  if (String(collection.jobEntryId) === id) return true;
  const raw = collection.allocationsJson || "[]";
  let allocations;
  try { allocations=JSON.parse(raw,(key,value,context)=>{
    if(key!=="jobId") return value;
    if(typeof value==="string") return value;
    if(context?.source) return context.source;
    if(Number.isSafeInteger(value)) return String(value);
    throw Error("Allocation identity cannot be represented safely");
  }); } catch (_) { throw Error("Collection allocation history is invalid; ask the Owner to reconcile it before deletion"); }
  if(!Array.isArray(allocations) || allocations.some(a=>!a || !/^[1-9][0-9]*$/.test(String(a.jobId)))) throw Error("Invalid allocation history");
  return allocations.some(a=>String(a.jobId)===id);
}
function requireUnpaid(entry, collections, id) {
  if (collections.some(c => references(c, id) && c.status !== "REVERSED")) throw Error("Reverse active Collections in Collection History before deleting this Entry");
  const total=Number(entry.totalAmount), paid=Number(entry.amountReceived), due=Number(entry.pendingAmount);
  if (![total,paid,due].every(Number.isFinite) || total<0 || paid!==0 || Math.abs(total-due)>=0.01) throw Error("Entry balances must be reconciled before deletion");
}
function plannedCollectionDeletes(entry, collections, id, role, uid, actionTime) {
  const linked=collections.filter(c=>references(c,id));
  if(linked.some(c=>c.status!=="ACTIVE" && c.status!=="REVERSED")) throw Error("Collection history needs reconciliation before deletion");
  const active=linked.filter(c=>c.status==="ACTIVE");
  let total=0;
  for(const collection of active) {
    let allocations;
    try { allocations=JSON.parse(collection.allocationsJson || "[]",(key,value,context)=>{
      if(key!=="jobId") return value;
      if(typeof value==="string") return value;
      if(context?.source) return context.source;
      if(Number.isSafeInteger(value)) return String(value);
      throw Error("Unsafe Collection allocation identity");
    }); }
    catch { throw Error("Collection allocations need reconciliation before deletion"); }
    const amount=Number(collection.amount);
    if(!Array.isArray(allocations) || allocations.length!==1 || String(allocations[0]?.jobId)!==id ||
       !Number.isFinite(amount) || amount<=0 || Number(allocations[0]?.amount)!==amount ||
       String(collection.customerId)!==String(entry.customerId) || collection.workspaceId!==entry.workspaceId)
      throw Error("Shared or inconsistent Collection allocations need separate reversal");
    if(role!=="OWNER" && (role!=="PARTNER" || collection.collectedByUid!==uid ||
       !Number.isSafeInteger(Number(collection.createdAt)) || actionTime<Number(collection.createdAt) ||
       actionTime-Number(collection.createdAt)>=86400000))
      throw Error("You cannot delete this linked Collection; ask its authorized collector or Owner");
    total+=amount;
  }
  const billed=Number(entry.totalAmount), paid=Number(entry.amountReceived), due=Number(entry.pendingAmount);
  // The user-approved future explicit Owner deletion also removes a pre-collection
  // embedded receipt. This is never a cleanup/migration or a bulk-delete instruction.
  const embeddedLegacyReceipt = role==="OWNER" && linked.length===0 && !entry.collectionId &&
    entry.protocolVersion==null && !entry.authorizationTimeStatus && paid>=0 && paid<=billed;
  if(![billed,paid,due,total].every(Number.isFinite) || billed<0 || (!embeddedLegacyReceipt && Math.abs(paid-total)>=0.01) ||
     Math.abs(billed-paid-due)>=0.01)
    throw Error("Entry and Collection balances need reconciliation before deletion");
  return active;
}
function customerTotals(entries) {
  const jobs=entries.filter(e=>!e.isDeleted);
  const work=jobs.filter(e=>String(e.tractorLabel).toLowerCase()!=="payment" && String(e.workType).toLowerCase()!=="payment received");
  if(work.length===0) return {totalBilled:0,totalPaid:0,balanceDue:0};
  const billed=work.reduce((sum,e)=>sum+(Number(e.totalAmount)>0?Number(e.totalAmount):Number(e.amountReceived)+Number(e.pendingAmount)),0);
  const paid=jobs.reduce((sum,e)=>sum+Number(e.amountReceived),0);
  if(![billed,paid].every(Number.isFinite)) throw Error("Customer balances contain invalid records");
  const round=value=>Math.max(0,Math.round(value));
  return {totalBilled:round(billed),totalPaid:round(paid),balanceDue:Math.max(0,round(billed)-round(paid))};
}
function customerAfterEntryDeletion(customerId, retainedEntries, retainedCollections, entryId) {
  const entries = retainedEntries.filter(e => String(e.customerId) === customerId);
  const collections = retainedCollections.filter(c => String(c.customerId) === customerId ||
    (entryId !== undefined && references(c, entryId)));
  return { deleteCustomer: entries.length === 0 && collections.length === 0,
    totals: customerTotals(entries) };
}
module.exports={references, requireUnpaid,plannedCollectionDeletes,customerTotals,customerAfterEntryDeletion};
