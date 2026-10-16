// End-to-end quota test against a local worker and a fake Anthropic API. No real key, no cost.
//
//   npm run db:local && npm test
//
// Starts `wrangler dev` itself; needs .dev.vars (written below if missing).
import { execSync, spawn } from "node:child_process";
import { createServer } from "node:http";
import { readFileSync, writeFileSync } from "node:fs";
import { createPrivateKey, randomBytes, sign } from "node:crypto";

const FAKE_PORT = 8788;
const PORT = 8787;
const APP_KEY = "test-app-key";
const ADMIN_KEY = "test-admin-key";

// Test-only store credentials from test/fixtures (bash test/fixtures/make.sh): our own "Apple" root and a
// fake Google service account, both answered by the fake server below.
const fixture = (name) => readFileSync(new URL(`./fixtures/${name}`, import.meta.url), "utf8").trim();
writeFileSync(".dev.vars", [
  "ANTHROPIC_API_KEY=test", `APP_KEY=${APP_KEY}`, `ADMIN_KEY=${ADMIN_KEY}`, `ANTHROPIC_BASE_URL=http://127.0.0.1:${FAKE_PORT}`,
  `APPLE_ROOT_B64=${fixture("root.b64")}`, "GOOGLE_SA_EMAIL=test@calocare.iam.gserviceaccount.com",
  `GOOGLE_SA_KEY=${fixture("google.pk8").replace(/\r?\n/g, "\\n")}`, `GOOGLE_API_BASE=http://127.0.0.1:${FAKE_PORT}`,
].join("\n") + "\n");

/** A StoreKit 2 signed transaction, signed like the App Store does but with the test chain. */
function appleTransaction(payload, { tamper = false } = {}) {
  const enc = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const header = enc({ alg: "ES256", x5c: ["leaf", "intermediate", "root"].map((c) => fixture(`${c}.b64`)) });
  const body = enc(payload);
  const signature = sign("sha256", Buffer.from(`${header}.${body}`), { key: createPrivateKey(fixture("leaf.pk8")), dsaEncoding: "ieee-p1363" }).toString("base64url");
  return `${header}.${tamper ? enc({ ...payload, expiresDate: payload.expiresDate + 3e10 }) : body}.${signature}`;
}

/** What the fake Google Play says about each purchase token. */
const playTokens = {
  "play-active": { subscriptionState: "SUBSCRIPTION_STATE_ACTIVE", acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING", lineItems: [{ productId: "calocare_pro", expiryTime: new Date(Date.now() + 30 * 864e5).toISOString() }] },
  "play-expired": { subscriptionState: "SUBSCRIPTION_STATE_EXPIRED", lineItems: [{ productId: "calocare_pro", expiryTime: new Date(Date.now() - 864e5).toISOString() }] },
};
let acknowledged = [];

let failNext = false;
/** The last request the worker sent to the fake API, to check what the model was asked. */
let lastRequest = null;
const fake = createServer((req, res) => {
  let raw = "";
  req.on("data", (c) => (raw += c));
  req.on("end", () => {
    // Fake Google: sign-in, subscription lookup and acknowledgement.
    if (req.url === "/token") {
      res.writeHead(200, { "content-type": "application/json" });
      return res.end(JSON.stringify({ access_token: "fake-google-token", expires_in: 3600 }));
    }
    if (req.url.startsWith("/androidpublisher/")) {
      if (req.headers.authorization !== "Bearer fake-google-token") { res.writeHead(401); return res.end("{}"); }
      const token = decodeURIComponent(req.url.split("/tokens/")[1].split(":")[0]);
      if (req.url.endsWith(":acknowledge")) { acknowledged.push(token); res.writeHead(200); return res.end("{}"); }
      const sub = playTokens[token];
      res.writeHead(sub ? 200 : 404, { "content-type": "application/json" });
      return res.end(JSON.stringify(sub ?? { error: { code: 404 } }));
    }
    if (failNext) {
      failNext = false;
      res.writeHead(400, { "content-type": "application/json" });
      return res.end(JSON.stringify({ type: "error", error: { type: "invalid_request_error", message: "boom" } }));
    }
    const body = JSON.parse(raw);
    lastRequest = body;
    const structured = !!body.output_config?.format;
    const text = structured
      ? JSON.stringify({ items: [{ name: "Phở bò", alternatives: [], emoji: "🍜", portion: "1 tô", size: "medium", qty: 1, kcal: 450, protein_g: 25, carbs_g: 60, fat_g: 12, course: "main", tags: ["meat"], confidence: 0.8 }], advice: "ok" })
      : "Bạn nên ăn thêm rau.";
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ id: "msg_test", type: "message", role: "assistant", model: body.model, content: [{ type: "text", text }], stop_reason: "end_turn", stop_sequence: null, usage: { input_tokens: 1, output_tokens: 1 } }));
  });
}).listen(FAKE_PORT);

const worker = spawn("npx", ["wrangler", "dev", "--port", String(PORT)], { shell: true, stdio: ["ignore", "pipe", "pipe"] });
let log = "";
worker.stdout.on("data", (d) => (log += d));
worker.stderr.on("data", (d) => (log += d));

const base = `http://127.0.0.1:${PORT}`;
const device = () => randomBytes(32).toString("hex");
const call = (path, dev, body, key = APP_KEY, lang = "vi") =>
  fetch(base + path, {
    method: body ? "POST" : "GET",
    headers: { "x-app-key": key, "x-device": dev, "content-type": "application/json", "x-lang": lang },
    body: body ? JSON.stringify(body) : undefined,
  }).then(async (r) => ({ status: r.status, body: await r.json() }));

let failures = 0;
const check = (name, ok, detail = "") => {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${ok ? "" : "  " + detail}`);
  if (!ok) failures++;
};

async function waitReady() {
  for (let i = 0; i < 90; i++) {
    try {
      await fetch(base + "/v1/quota");
      return;
    } catch {
      await new Promise((r) => setTimeout(r, 1000));
    }
  }
  throw new Error("worker did not start:\n" + log);
}

try {
  await waitReady();
  const photo = { image: "aGVsbG8=", context: "test" };
  const chat = { context: "test", history: [{ role: "user", text: "Tối nay ăn gì?" }] };

  let r = await call("/v1/quota", device(), null, "wrong");
  check("rejects a wrong app key", r.status === 401, JSON.stringify(r));
  r = await call("/v1/quota", "not-a-device");
  check("rejects a malformed device id", r.status === 400, JSON.stringify(r));

  const a = device();
  r = await call("/v1/quota", a);
  check("new device starts with the welcome allowance", r.body.quota?.trial_photos_left === 30 && r.body.quota?.trial_chats_left === 50 && r.body.quota?.photos_left_today === 30 && r.body.quota?.pro === false, JSON.stringify(r.body));

  r = await call("/v1/photo", a, photo);
  check("photo returns the analysis", r.status === 200 && r.body.result?.items?.[0]?.kcal === 450 && r.body.quota.trial_photos_left === 29, JSON.stringify(r));
  r = await call("/v1/chat", a, chat);
  check("chat returns text", r.status === 200 && typeof r.body.result?.text === "string" && r.body.quota.trial_chats_left === 49, JSON.stringify(r));
  // A name no earlier run can have left in the shared estimate cache, which answers without drawing on the allowance.
  r = await call("/v1/estimate", a, { name: `món thử ${Date.now()}` });
  check("estimate draws on the chat allowance", r.status === 200 && r.body.quota.trial_chats_left === 48, JSON.stringify(r));

  failNext = true;
  r = await call("/v1/photo", a, photo);
  const after = await call("/v1/quota", a);
  check("a failed AI call is refunded", r.status === 502 && after.body.quota.trial_photos_left === 29, JSON.stringify([r, after.body]));
  check("upstream error text is not leaked", !JSON.stringify(r.body).includes("boom"), JSON.stringify(r.body));

  // Welcome: 30 photos; after them, the 2 free photos of a day are already used up the same day.
  const b = device();
  let ok = 0;
  for (let i = 0; i < 30; i++) ok += (await call("/v1/photo", b, photo)).status === 200 ? 1 : 0;
  r = await call("/v1/photo", b, photo);
  check("after the 30 welcome photos the app is asked to offer Pro", ok === 30 && r.status === 402 && r.body.error === "pro_required", `ok=${ok} ${JSON.stringify(r)}`);

  // After the welcome allowance: 2 photos and 5 chats a day. Fast-forward the counters directly in the local DB.
  const sql = (q) => new Promise((res, rej) => {
    const p = spawn("npx", ["wrangler", "d1", "execute", "calocare", "--local", "--command", JSON.stringify(q)], { shell: true, stdio: "ignore" });
    p.on("exit", (c) => (c === 0 ? res() : rej(new Error("sql failed: " + q))));
  });
  const c = device();
  await call("/v1/quota", c);
  await sql(`UPDATE usage SET photos_total = 100, chats_total = 100, photos_day = 0, chats_day = 0 WHERE device = '${c}'`);
  r = await call("/v1/quota", c);
  check("free users then get 2 photos and 5 chats a day", r.body.quota.photos_left_today === 2 && r.body.quota.chats_left_today === 5, JSON.stringify(r.body));
  ok = 0;
  for (let i = 0; i < 2; i++) ok += (await call("/v1/photo", c, photo)).status === 200 ? 1 : 0;
  r = await call("/v1/photo", c, photo);
  check("the 3rd free photo of the day offers Pro", ok === 2 && r.status === 402 && r.body.error === "pro_required", `ok=${ok} ${JSON.stringify(r)}`);
  ok = 0;
  for (let i = 0; i < 5; i++) ok += (await call("/v1/chat", c, chat)).status === 200 ? 1 : 0;
  r = await call("/v1/chat", c, chat);
  check("the 6th free chat of the day offers Pro", ok === 5 && r.status === 402, `ok=${ok} ${JSON.stringify(r)}`);
  r = await call("/v1/chat", c, chat, APP_KEY, "en");
  check("an English app is told about its limit in English", r.status === 402 && r.body.message.startsWith("You've used today's"), JSON.stringify(r));

  // CaloCare Pro bought on an iPhone: the signed transaction is checked against the root certificate.
  const appleTx = { bundleId: "com.calocare.app", productId: "com.napigo.calocare.pro.yearly", originalTransactionId: `orig-${Date.now()}`, transactionId: "t1", expiresDate: Date.now() + 365 * 864e5, environment: "Sandbox" };
  r = await call("/v1/pro", c, { platform: "ios", transaction: appleTransaction(appleTx, { tamper: true }) });
  check("a tampered App Store transaction is refused", r.status === 400 && r.body.error === "bad_purchase", JSON.stringify(r));
  r = await call("/v1/pro", c, { platform: "ios", transaction: appleTransaction({ ...appleTx, bundleId: "com.other.app" }) });
  check("another app's transaction is refused", r.status === 400, JSON.stringify(r));
  r = await call("/v1/pro", c, { platform: "ios", transaction: appleTransaction(appleTx) });
  check("a valid App Store purchase turns on Pro", r.status === 200 && r.body.quota.pro === true && r.body.quota.photos_left_today === 38, JSON.stringify(r));
  r = await call("/v1/photo", c, photo);
  check("Pro photographs past the free limit", r.status === 200, JSON.stringify(r));
  const lapsed = device();
  r = await call("/v1/pro", lapsed, { platform: "ios", transaction: appleTransaction({ ...appleTx, originalTransactionId: "old", expiresDate: Date.now() - 864e5 }) });
  check("an expired subscription does not give Pro", r.status === 200 && r.body.quota.pro === false, JSON.stringify(r));

  // CaloCare Pro bought on Android: the token is checked with Google Play, and acknowledged.
  // Devices linked by earlier runs would count against the per-purchase device limit.
  await sql("DELETE FROM pro WHERE platform = 'android'");
  const g = device();
  r = await call("/v1/pro", g, { platform: "android", token: "play-active" });
  check("an active Play subscription turns on Pro and is acknowledged", r.status === 200 && r.body.quota.pro === true && acknowledged.includes("play-active"), JSON.stringify(r));
  r = await call("/v1/pro", device(), { platform: "android", token: "play-expired" });
  check("an expired Play subscription does not give Pro", r.status === 200 && r.body.quota.pro === false, JSON.stringify(r));
  r = await call("/v1/pro", device(), { platform: "android", token: "made-up" });
  check("an unknown Play token is refused", r.status === 400, JSON.stringify(r));
  for (let i = 0; i < 4; i++) await call("/v1/pro", device(), { platform: "android", token: "play-active" });
  r = await call("/v1/pro", device(), { platform: "android", token: "play-active" });
  check("one subscription unlocks at most 5 devices", r.status === 409 && r.body.error === "too_many_devices", JSON.stringify(r));

  // The language of the answer: one line in the request; the cached system prompt is the same for both languages.
  const e = device();
  await call("/v1/photo", e, photo, APP_KEY, "en");
  const enPhoto = lastRequest;
  await call("/v1/photo", e, photo);
  const viPhoto = lastRequest;
  check("an English photo request asks for English answers", JSON.stringify(enPhoto.messages).includes("LANGUAGE: the user reads English") && !JSON.stringify(viPhoto.messages).includes("LANGUAGE:"), "");
  check("the cached photo prompt is the same in both languages", JSON.stringify(enPhoto.system[0]) === JSON.stringify(viPhoto.system[0]), "");
  await call("/v1/chat", e, chat, APP_KEY, "en");
  check("the English coach replies in English", lastRequest.system.includes("Reply in English"), lastRequest.system.slice(0, 200));
  const page = await fetch(base + "/privacy?lang=en").then((x) => x.text());
  check("the privacy policy is published in English too", page.includes("CaloCare – Privacy Policy"), page.slice(0, 120));

  // A new day restores the daily allowance but not the trial.
  await sql(`UPDATE usage SET day = '2000-01-01' WHERE device = '${c}'`);
  r = await call("/v1/quota", c);
  check("a new day restores Pro's daily allowance", r.body.quota.photos_left_today === 40 && r.body.quota.chats_left_today === 100 && r.body.quota.pro === true, JSON.stringify(r.body));

  // Learning from corrections. The fake AI always answers "Phở bò".
  await sql(`DELETE FROM fix_votes; DELETE FROM fixes; UPDATE names SET seen = 4`);

  // Vandalism: three phones insist phở is plain tea. The dishes are nothing alike, so the AI's answer
  // stays first for everyone else; the "correction" can only ever appear behind it.
  const vandals = [device(), device(), device()];
  for (const v of vandals) await call("/v1/correction", v, { ai_name: "Phở bò", chosen: "Trà không đường" });
  r = await call("/v1/photo", device(), photo);
  check("a nutritionally implausible correction never replaces the AI's answer", r.body.result?.items?.[0]?.name === "Phở bò", JSON.stringify(r.body.result));
  // Impossible numbers (200 kcal from nothing) are dropped outright.
  for (const v of vandals) await call("/v1/correction", v, { ai_name: "Món lạ", chosen: "Cơm trắng", ai: { kcal: 200, protein_g: 0, carbs_g: 0, fat_g: 0 } });
  await sql(`DELETE FROM fix_votes; DELETE FROM fixes; UPDATE names SET seen = 100`);

  const fix = { ai_name: "Phở bò", chosen: "Bún bò Huế" };
  const voters = [device(), device(), device()];
  await call("/v1/correction", voters[0], fix);
  await call("/v1/correction", voters[0], fix); // the same phone again must not count twice
  r = await call("/v1/photo", device(), photo);
  check("one device's correction changes nothing for others", r.body.result?.items?.[0]?.name === "Phở bò" && r.body.result.items[0].alternatives.length === 0, JSON.stringify(r.body.result));
  await call("/v1/correction", voters[1], fix);
  await call("/v1/correction", voters[2], fix);
  r = await call("/v1/photo", device(), photo);
  check("a correction several devices agree on is offered as an alternative", r.body.result?.items?.[0]?.name === "Phở bò" && r.body.result.items[0].alternatives.includes("Bún bò Huế"), JSON.stringify(r.body.result));
  // The AI's name was corrected by most of the people who got it: rename automatically, keep the AI's name as the fallback.
  await sql(`UPDATE names SET seen = 4`);
  r = await call("/v1/photo", device(), photo);
  check("a name that is usually wrong is corrected automatically", r.body.result?.items?.[0]?.name === "Bún bò Huế" && r.body.result.items[0].alternatives.includes("Phở bò"), JSON.stringify(r.body.result));

  // The dish library: seeded dishes reach the phone, a user's new dish waits for the owner, approval publishes it.
  const admin = (path, body, key = ADMIN_KEY) =>
    fetch(base + "/admin/api/" + path, { method: body ? "POST" : "GET", headers: { "x-admin-key": key, "content-type": "application/json" }, body: body ? JSON.stringify(body) : undefined })
      .then(async (r) => ({ status: r.status, body: await r.json() }));
  const dishName = `Món thử ${Date.now()}`;
  const newDish = { name: dishName, emoji: "🍲", serving: "1 tô", kcal: 400, protein_g: 20, carbs_g: 50, fat_g: 13, course: "main", tags: ["meat"] };

  r = await call("/v1/dishes?since=0", device());
  check("a phone's first sync gets the seeded library", r.status === 200 && r.body.dishes.some((d) => d.id === "pho_bo" && d.kcal === 450) && r.body.version >= 1, JSON.stringify(r.body).slice(0, 200));
  const version = r.body.version;

  await call("/v1/dish", device(), { ...newDish, kcal: 400, protein_g: 0, carbs_g: 0, fat_g: 0 });
  r = await admin("dishes?status=pending");
  check("a dish with impossible numbers is not even queued", r.status === 200 && !r.body.items.some((d) => d.name === dishName), JSON.stringify(r.body).slice(0, 200));

  await call("/v1/dish", device(), newDish);
  await call("/v1/dish", device(), { ...newDish, kcal: 440, protein_g: 22, carbs_g: 55, fat_g: 14.5 });
  r = await admin("dishes?status=pending");
  const queued = r.body.items.find((d) => d.name === dishName);
  check("a user's new dish waits in the owner's queue, averaged over its submitters", queued?.devices === 2 && queued.kcal === 420, JSON.stringify(queued));
  r = await call(`/v1/dishes?since=${version}`, device());
  check("a pending dish is not sent to phones", !r.body.dishes.some((d) => d.name === dishName), JSON.stringify(r.body).slice(0, 200));

  r = await admin("dishes?status=pending", null, "wrong");
  check("the admin API rejects a wrong key", r.status === 401, JSON.stringify(r));
  r = await admin("dish", { ...queued, tags: "meat", status: "published", kcal: 999 });
  check("the owner cannot publish numbers that do not add up either", r.status === 400, JSON.stringify(r));
  r = await admin("dish", { ...queued, tags: "meat", status: "published" });
  const synced = await call(`/v1/dishes?since=${version}`, device());
  check("an approved dish reaches phones on their next sync", r.status === 200 && synced.body.dishes.some((d) => d.name === dishName && d.kcal === 420) && synced.body.version > version, JSON.stringify([r, synced.body]).slice(0, 300));
  r = await admin("dish", { ...queued, tags: "meat", status: "rejected" });
  const gone = await call(`/v1/dishes?since=${synced.body.version}`, device());
  check("a dish taken out of the library is removed from phones", gone.body.removed.includes(queued.id), JSON.stringify(gone.body).slice(0, 200));
} catch (e) {
  console.error(e);
  failures++;
} finally {
  fake.close();
  // Kill the whole tree (shell → npx → wrangler → workerd) and wait for it: a surviving wrangler
  // respawns workerd and would answer the next run on the same port with stale settings.
  if (process.platform === "win32") {
    try { execSync(`taskkill /F /T /PID ${worker.pid}`, { stdio: "ignore" }); } catch {}
  } else worker.kill();
}
console.log(failures ? `\n${failures} check(s) failed` : "\nall checks passed");
process.exit(failures ? 1 : 0);
