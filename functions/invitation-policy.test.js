const test = require("node:test");
const assert = require("node:assert/strict");
const { validateInvitation } = require("./invitation-policy");
const invite = { ownerUid: "owner", status: "PENDING", role: "PARTNER", phone: "+919876543210", createdAt: 1000000, expiresAt: 1300000 };
const claims = { uid: "target", phone_number: invite.phone, auth_time: 1000, firebase: { sign_in_provider: "phone" } };
test("verified target joins with locked role", () => assert.deepEqual(validateInvitation(invite, "owner", claims, 1200000), { uid: "target", role: "PARTNER" }));
for (const [label, changed, actor, token, time] of [
  ["wrong owner", invite, "other", claims, 1200000],
  ["replayed invitation", { ...invite, status: "USED" }, "owner", claims, 1200000],
  ["expired production OTP", invite, "owner", claims, 1300000],
  ["older phone session", invite, "owner", { ...claims, auth_time: 900 }, 1200000],
  ["different verified phone", invite, "owner", { ...claims, phone_number: "+919999999999" }, 1200000],
  ["Owner role injection", { ...invite, role: "OWNER" }, "owner", claims, 1200000],
  ["Co-Owner injection", { ...invite, role: "CO-OWNER" }, "owner", claims, 1200000],
  ["email token", invite, "owner", { ...claims, firebase: { sign_in_provider: "password" } }, 1200000]
]) test(label, () => assert.throws(() => validateInvitation(changed, actor, token, time)));
test("emulator exemption still checks identity and single use", () => {
  assert.equal(validateInvitation(invite, "owner", claims, 999999999, true).uid, "target");
  assert.throws(() => validateInvitation({ ...invite, status: "USED" }, "owner", claims, 999999999, true));
});

const { validateAcceptedInvitation } = require('./invitation-policy');
const accepted={...invite,workspaceId:'workspace',status:'USED',acceptedByUid:'target'};
const receipt={targetUid:'target',workspaceId:'workspace',invitationId:'invite',authTime:1000};
const membership={uid:'target',workspaceId:'workspace',status:'ACTIVE',role:'PARTNER'};
test('confirmed invitation replay returns the same membership without another consumption',()=>{
 assert.deepEqual(validateAcceptedInvitation(accepted,'owner',claims,receipt,membership,'workspace','invite'),
 {uid:'target',workspaceId:'workspace',role:'PARTNER',alreadyAccepted:true});
});
for(const [label,changedReceipt,changedMember,changedClaims] of [
 ['missing receipt',null,membership,claims],
 ['other invitation',{...receipt,invitationId:'another'},membership,claims],
 ['wrong OTP session',receipt,membership,{...claims,auth_time:1001}],
 ['removed membership',receipt,{...membership,status:'REMOVED'},claims],
 ['wrong workspace',receipt,{...membership,workspaceId:'another'},claims],
 ['wrong role',receipt,{...membership,role:'OWNER'},claims],
 ['wrong phone',receipt,membership,{...claims,phone_number:'+919000000999'}]
])test(`accepted invitation rejects ${label}`,()=>assert.throws(()=>validateAcceptedInvitation(accepted,'owner',changedClaims,changedReceipt,changedMember,'workspace','invite')));
