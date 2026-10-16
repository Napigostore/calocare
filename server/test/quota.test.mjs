// End-to-end quota test against a local worker and a fake Anthropic API. No real key, no cost.
//
//   npm run db:local && npm test
//
// Starts `wrangler dev` itself; needs .dev.vars (written below if missing).
import { execSync, spawn } from "node:child_process";
import { createServer } from "node:http";
import { existsSync, writeFileSync } from "node:fs";
import { randomBytes } from "node:crypto";

const FAKE_PORT = 8788;
const PORT = 8787;
const APP_KEY = "test-app-key";
const ADMIN_KEY = "test-admin-key";

if (!existsSync(".dev.vars")) {
  writeFileSync(".dev.vars", `ANTHROPIC_API_KEY=test\nAPP_KEY=${APP_KEY}\nADMIN_KEY=${ADMIN_KEY}\nANTHROPIC_BASE_URL=http://127.0.0.1:${FAKE_PORT}\n`);
}

let failNext = false;
/** The last request the worker sent to the fake API, to check what the model was asked. */
let lastRequest = null;
const fake = createServer((req, res) => {
  let raw = "";
  req.on("data", (c) => (raw += c));
  req.on("end", () => {
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
  check("new device starts with the full trial", r.body.quota?.trial_photos_left === 100 && r.body.quota?.trial_chats_left === 100 && r.body.quota?.photos_left_today === 30, JSON.stringify(r.body));

  r = await call("/v1/photo", a, photo);
  check("photo returns the analysis", r.status === 200 && r.body.result?.items?.[0]?.kcal === 450 && r.body.quota.trial_photos_left === 99, JSON.stringify(r));
  r = await call("/v1/chat", a, chat);
  check("chat returns text", r.status === 200 && typeof r.body.result?.text === "string" && r.body.quota.trial_chats_left === 99, JSON.stringify(r));
  // A name no earlier run can have left in the shared estimate cache, which answers without drawing on the allowance.
  r = await call("/v1/estimate", a, { name: `món thử ${Date.now()}` });
  check("estimate draws on the chat allowance", r.status === 200 && r.body.quota.trial_chats_left === 98, JSON.stringify(r));

  failNext = true;
  r = await call("/v1/photo", a, photo);
  const after = await call("/v1/quota", a);
  check("a failed AI call is refunded", r.status === 502 && after.body.quota.trial_photos_left === 99, JSON.stringify([r, after.body]));
  check("upstream error text is not leaked", !JSON.stringify(r.body).includes("boom"), JSON.stringify(r.body));

  // Trial: at most 30 photos a day.
  const b = device();
  let ok = 0;
  for (let i = 0; i < 30; i++) ok += (await call("/v1/photo", b, photo)).status === 200 ? 1 : 0;
  r = await call("/v1/photo", b, photo);
  check("31st photo of the day is refused during the trial", ok === 30 && r.status === 429 && r.body.error === "quota", `ok=${ok} ${JSON.stringify(r)}`);

  // After the trial: 6 photos and 10 chats a day. Fast-forward the counters directly in the local DB.
  const sql = (q) => new Promise((res, rej) => {
    const p = spawn("npx", ["wrangler", "d1", "execute", "calocare", "--local", "--command", JSON.stringify(q)], { shell: true, stdio: "ignore" });
    p.on("exit", (c) => (c === 0 ? res() : rej(new Error("sql failed: " + q))));
  });
  const c = device();
  await call("/v1/quota", c);
  await sql(`UPDATE usage SET photos_total = 100, chats_total = 100, photos_day = 0, chats_day = 0 WHERE device = '${c}'`);
  r = await call("/v1/quota", c);
  check("after the trial the daily allowance is 6 photos and 10 chats", r.body.quota.photos_left_today === 6 && r.body.quota.chats_left_today === 10, JSON.stringify(r.body));
  ok = 0;
  for (let i = 0; i < 6; i++) ok += (await call("/v1/photo", c, photo)).status === 200 ? 1 : 0;
  r = await call("/v1/photo", c, photo);
  check("7th photo of the day is refused after the trial", ok === 6 && r.status === 429, `ok=${ok} ${JSON.stringify(r)}`);
  ok = 0;
  for (let i = 0; i < 10; i++) ok += (await call("/v1/chat", c, chat)).status === 200 ? 1 : 0;
  r = await call("/v1/chat", c, chat);
  check("11th chat of the day is refused after the trial", ok === 10 && r.status === 429, `ok=${ok} ${JSON.stringify(r)}`);
  r = await call("/v1/chat", c, chat, APP_KEY, "en");
  check("an English app is told about its limit in English", r.status === 429 && r.body.message.startsWith("You've used all"), JSON.stringify(r));

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
  check("a new day restores the daily allowance", r.body.quota.photos_left_today === 6 && r.body.quota.chats_left_today === 10 && r.body.quota.trial_photos_left === 0, JSON.stringify(r.body));

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
