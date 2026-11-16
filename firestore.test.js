const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DB_ID = "ai-studio-android-framecra-ae78970f-7aa7-45d7-8644-0238f4f29b6f";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validTimestamp() {
  return new Date(Date.now() - 1000);
}

function validProjectPayload(uid, overrides = {}) {
  const ts = validTimestamp();
  return {
    userId: uid,
    title: "Neon Tokyo Reel",
    prompt: "Cyberpunk rain walk with glowing neon reflections",
    mediaType: "VIDEO",
    aspectRatio: "9:16",
    stylePreset: "CYBERPUNK",
    colorGrade: "CYBER_TEAL",
    exposure: 0.0,
    contrast: 1.1,
    saturation: 1.2,
    warmth: -0.1,
    overlayText: "TOKYO 2099",
    localMediaPath: "/data/user/0/app/files/reel_1.mp4",
    scenes: [
      {
        sceneIndex: 0,
        visualPrompt: "Wide shot of wet neon street",
        caption: "Midnight arrival",
        cameraMotion: "ZOOM_IN",
        transition: "CROSSFADE",
        durationMs: 3000,
        localFramePath: "/data/user/0/app/files/frame_0.png",
      },
    ],
    editHistory: ["Initial generation"],
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
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

test("Unauthenticated user cannot list or write projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DB_ID);
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/projects`).get());
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/projects`)
      .doc("proj_1")
      .set(validProjectPayload(ALICE_UID))
  );
});

test("Authenticated user can create, read, update, list, and delete their own project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DB_ID);
  const projRef = aliceDb.collection(`users/${ALICE_UID}/projects`).doc("proj_1");

  await assertSucceeds(projRef.set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(projRef.get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/projects`).get());
  await assertSucceeds(
    projRef.update({
      title: "Neon Tokyo Director Cut",
      colorGrade: "NOIR_BW",
      updatedAt: validTimestamp(),
    })
  );
  await assertSucceeds(projRef.delete());
});

test("Cross-user access is strictly denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DB_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DB_ID);

  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/projects`)
      .doc("alice_proj")
      .set(validProjectPayload(ALICE_UID))
  );

  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/projects`).doc("alice_proj").get()
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/projects`).get());
  await assertFails(
    bobDb
      .collection(`users/${ALICE_UID}/projects`)
      .doc("alice_proj")
      .update({ title: "Hijacked", updatedAt: validTimestamp() })
  );
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/projects`).doc("alice_proj").delete()
  );
});

test("Shadow update with ghost field or immutable field mutation fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DB_ID);
  const projRef = aliceDb.collection(`users/${ALICE_UID}/projects`).doc("proj_2");

  await assertFails(
    projRef.set(validProjectPayload(ALICE_UID, { isAdmin: true }))
  );

  await assertSucceeds(projRef.set(validProjectPayload(ALICE_UID)));

  await assertFails(
    projRef.update({
      mediaType: "IMAGE",
      updatedAt: validTimestamp(),
    })
  );

  await assertFails(
    projRef.update({
      userId: BOB_UID,
      updatedAt: validTimestamp(),
    })
  );
});

test("Invalid numeric bounds, invalid enum, or poisoned document ID fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DB_ID);

  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/projects`)
      .doc("bad id with spaces!")
      .set(validProjectPayload(ALICE_UID))
  );

  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/projects`)
      .doc("proj_bad_enum")
      .set(validProjectPayload(ALICE_UID, { mediaType: "AUDIO" }))
  );

  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/projects`)
      .doc("proj_bad_exposure")
      .set(validProjectPayload(ALICE_UID, { exposure: 99.0 }))
  );
});
