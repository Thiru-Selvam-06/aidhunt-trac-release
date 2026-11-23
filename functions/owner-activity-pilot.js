"use strict";

const PRODUCTION_PROJECT = "trac-aidhunt";
const DEMO_PROJECT = "demo-aidhunt-v16";
const SESSION_SERVICE_ACCOUNT = "trac-notify-session@trac-aidhunt.iam.gserviceaccount.com";
const SENDER_SERVICE_ACCOUNT = "trac-notify-sender@trac-aidhunt.iam.gserviceaccount.com";
const validIdentity = value => typeof value === "string" && /^[A-Za-z0-9_-]{1,160}$/.test(value);

/**
 * Returns the only Owner UID the production pilot may use. Demo emulators retain
 * their fixture isolation without enabling live FCM or widening the live scope.
 */
function ownerActivityPilotScope({ projectId, emulator, enabled, pilotWorkspaceId, pilotOwnerUid },
  workspaceId, sourceProjectId = projectId) {
  if (sourceProjectId !== projectId || !validIdentity(workspaceId)) return null;
  if (projectId === DEMO_PROJECT && emulator === true) return { expectedOwnerUid: null };
  if (projectId !== PRODUCTION_PROJECT || emulator || enabled !== true ||
      !validIdentity(pilotWorkspaceId) || !validIdentity(pilotOwnerUid) ||
      workspaceId !== pilotWorkspaceId) return null;
  return { expectedOwnerUid: pilotOwnerUid };
}

module.exports = { ownerActivityPilotScope, SESSION_SERVICE_ACCOUNT, SENDER_SERVICE_ACCOUNT };
