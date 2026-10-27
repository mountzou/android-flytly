// Invoke the unchanged iOS production cleanup handler only against loopback demo emulators.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const { createRequire } = require('node:module');
const project = 'demo-flytly-lifecycle';
assert.equal(process.env.GCLOUD_PROJECT, project);
assert.equal(process.env.GOOGLE_CLOUD_PROJECT, project);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, '127.0.0.1:19099');
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:18080');
process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: project });
const fixture = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
assert.equal(fixture.project, project);
assert.equal(typeof fixture.deletedUID, 'string');
const functionsRoot = path.resolve(process.env.FLYTLY_IOS_ROOT || '../ios-flytly', 'functions');
const { cleanupDeletedUserData } = require(path.join(functionsRoot, 'index.js'));
const requireSource = createRequire(path.join(functionsRoot, 'package.json'));
const { getAuth } = requireSource('firebase-admin/auth');
const { getFirestore } = requireSource('firebase-admin/firestore');
const { getApp, deleteApp } = requireSource('firebase-admin/app');
(async () => {
    const db = getFirestore(); const auth = getAuth();
    await assert.rejects(auth.getUser(fixture.deletedUID), e => e.code === 'auth/user-not-found');
    const target = db.collection('users').doc(fixture.deletedUID);
    assert.equal((await target.collection('profile').doc('travelPreferences').get()).exists, true);
    assert.equal((await target.collection('travelPlaces').get()).size, 1);
    const other = await auth.createUser({ uid: `android-control-${randomUUID()}` });
    const control = db.collection('users').doc(other.uid).collection('profile').doc('travelPreferences');
    const global = db.collection('jevUsageGlobal').doc(`android-qa-${randomUUID()}`);
    const controlData = { travelPace: 3, completed: true };
    const globalData = { requestCount: 7, inputTokens: 100 };
    try {
        await control.set(controlData); await global.set(globalData);
        await target.collection('travelSyncSources').doc('android-qa').set({ lastSequence: 1 });
        await target.collection('travelPlaces').doc('country:GRC').collection('nested').doc('qa').set({ value: 1 });
        await db.collection('jevUsage').doc(fixture.deletedUID).collection('days').doc('qa').set({ requestCount: 1 });
        await cleanupDeletedUserData.run({ data: { uid: fixture.deletedUID } });
        await cleanupDeletedUserData.run({ data: { uid: fixture.deletedUID } });
        assert.equal((await target.get()).exists, false);
        assert.equal((await target.listCollections()).length, 0);
        assert.equal((await db.collection('jevUsage').doc(fixture.deletedUID).listCollections()).length, 0);
        assert.deepEqual((await control.get()).data(), controlData);
        assert.deepEqual((await global.get()).data(), globalData);
        assert.equal((await auth.getUser(other.uid)).uid, other.uid);
        console.log('ANDROID_DELETION_CLEANUP=' + JSON.stringify({
            project, sdkAuthDeletionConfirmed: true, profileDeleted: true, travelsDeleted: true,
            syncReceiptsDeleted: true, usageDeleted: true, duplicateEventSafe: true,
            otherUserPreserved: true, globalUsagePreserved: true
        }));
    } finally {
        await db.recursiveDelete(db.collection('users').doc(other.uid));
        await auth.deleteUser(other.uid); await global.delete(); await deleteApp(getApp());
    }
})().catch(() => { console.error('Android demo cleanup verification failed.'); process.exitCode = 1; });
