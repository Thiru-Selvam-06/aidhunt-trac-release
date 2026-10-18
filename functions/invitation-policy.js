"use strict";
function validateInvitation(invitation, actorUid, targetClaims, now, emulator = false) {
  if (invitation.ownerUid !== actorUid || invitation.status !== "PENDING") throw new Error("Invitation is unavailable or already used");
  if (!["PARTNER", "OPERATOR"].includes(invitation.role)) throw new Error("Invalid role");
  if (targetClaims.phone_number !== invitation.phone || !targetClaims.uid || targetClaims.uid === actorUid) throw new Error("Verified target phone does not match the invitation");
  if (targetClaims.firebase?.sign_in_provider !== "phone") throw new Error("Phone OTP verification is required");
  if (!Number.isFinite(targetClaims.auth_time) || !Number.isFinite(invitation.createdAt) || !Number.isFinite(invitation.expiresAt)) throw new Error("Verification time is unavailable");
  if (!emulator && (now >= invitation.expiresAt || targetClaims.auth_time * 1000 < invitation.createdAt - 1000)) throw new Error("Invitation OTP expired or was verified before this invitation");
  return { uid: targetClaims.uid, role: invitation.role };
}
function validateAcceptedInvitation(invitation, actorUid, claims, receipt, membership, workspaceId, invitationId) {
  if (invitation.status !== "USED") return null;
  if (invitation.ownerUid !== actorUid || invitation.workspaceId !== workspaceId ||
      invitation.acceptedByUid !== claims.uid || invitation.phone !== claims.phone_number ||
      claims.firebase?.sign_in_provider !== "phone" || !["PARTNER", "OPERATOR"].includes(invitation.role) ||
      !receipt || receipt.targetUid !== claims.uid || receipt.workspaceId !== workspaceId ||
      receipt.invitationId !== invitationId || Number(receipt.authTime) !== claims.auth_time ||
      !membership || String(membership.status).toUpperCase() !== "ACTIVE" ||
      String(membership.role).toUpperCase() !== invitation.role ||
      (membership.uid != null && membership.uid !== claims.uid) ||
      (membership.workspaceId != null && membership.workspaceId !== workspaceId))
    throw new Error("Accepted invitation no longer matches verified membership and OTP receipt");
  return { uid: claims.uid, workspaceId, role: invitation.role, alreadyAccepted: true };
}
module.exports = { validateInvitation, validateAcceptedInvitation };
