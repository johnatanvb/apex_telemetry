const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_f1_driver";
const BOB_UID = "bob_f1_rival";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validSessionPayload(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    gameTitle: "GRAN_TURISMO_7",
    carCategory: "GT_ENDURANCE",
    trackName: "Interlagos (São Paulo)",
    sessionType: "Corrida",
    weather: "Seco",
    sourceMode: "SIMULATOR",
    bestLapMs: 71482,
    avgLapMs: 72190,
    topSpeedKmh: 334,
    consistencyPct: 96.4,
    totalLaps: 12,
    finishPosition: 3,
    pitStops: 1,
    primaryCompound: "M",
    trackTempC: 36,
    notes: "Setup estável no S do Senna",
    createdAt: now,
    updatedAt: now,
  };
}

function validLapPayload(uid, sessionId, lapNumber = 1, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    sessionId,
    lapNumber,
    lapTimeMs: 71482,
    sector1Ms: 18420,
    sector2Ms: 36110,
    sector3Ms: 16952,
    tyreCompound: "M",
    topSpeedKmh: 334,
    avgThrottlePct: 74,
    maxTyreWearPct: 18,
    speedTrace: [295, 318, 334, 145, 210, 288, 165, 240, 315],
    createdAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write sessions or laps", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/sessions`).get());
  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/sessions/sess_1`).set(validSessionPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/sessions/sess_1/laps/lap_1`).set(validLapPayload(ALICE_UID, "sess_1"))
  );
});

test("Owner can atomically batch-create session and laps and list them", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const batch = aliceDb.batch();
  const sessionRef = aliceDb.doc(`users/${ALICE_UID}/sessions/sess_race_1`);
  const lapRef = aliceDb.doc(`users/${ALICE_UID}/sessions/sess_race_1/laps/lap_1`);

  batch.set(sessionRef, validSessionPayload(ALICE_UID));
  batch.set(lapRef, validLapPayload(ALICE_UID, "sess_race_1", 1));

  await assertSucceeds(batch.commit());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/sessions`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/sessions/sess_race_1/laps`).get());
});

test("Owner can update allowed session notes and metrics while preserving createdAt", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionRef = aliceDb.doc(`users/${ALICE_UID}/sessions/sess_race_2`);
  await assertSucceeds(sessionRef.set(validSessionPayload(ALICE_UID)));

  await assertSucceeds(
    sessionRef.update({
      notes: "Asa traseira +1 melhorou tração",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    sessionRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject ghost field
  await assertFails(
    sessionRef.update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Cross-user access is strictly blocked", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.doc(`users/${BOB_UID}/sessions/bob_sess`).set(validSessionPayload(BOB_UID));
    await adminDb
      .doc(`users/${BOB_UID}/sessions/bob_sess/laps/lap_1`)
      .set(validLapPayload(BOB_UID, "bob_sess", 1));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/sessions`).get());
  await assertFails(aliceDb.doc(`users/${BOB_UID}/sessions/bob_sess`).get());
  await assertFails(
    aliceDb.doc(`users/${BOB_UID}/sessions/bob_sess`).update({
      notes: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(aliceDb.doc(`users/${BOB_UID}/sessions/bob_sess`).delete());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/sessions/bob_sess/laps`).get());
});

test("Rejects invalid payloads, @DocumentId id field, future timestamps, and orphan laps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Reject explicit 'id' key
  const withId = { ...validSessionPayload(ALICE_UID), id: "sess_bad" };
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/sessions/sess_bad`).set(withId));

  // Reject future timestamp
  const future = new Date(Date.now() + 60000);
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/sessions/sess_future`).set(validSessionPayload(ALICE_UID, future))
  );

  // Reject invalid compound
  const badCompound = { ...validSessionPayload(ALICE_UID), primaryCompound: "X" };
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/sessions/sess_comp`).set(badCompound));

  // Reject orphan lap when parent session does not exist
  await assertFails(
    aliceDb
      .doc(`users/${ALICE_UID}/sessions/missing_sess/laps/lap_1`)
      .set(validLapPayload(ALICE_UID, "missing_sess", 1))
  );
});
