// Measures real token use and cost per request against a running server (deployed or local).
// Spends a few real API calls.
//
//   node test/measure.mjs https://calocare-api.<subdomain>.workers.dev <appKey> [photo.jpg ...]
//
// Prices are Claude Haiku 5.5 list prices in USD per million tokens.
import { readFileSync } from "node:fs";
import { randomBytes } from "node:crypto";

const [base, appKey, ...photos] = process.argv.slice(2);
if (!base || !appKey) {
  console.error("usage: node test/measure.mjs <server url> <app key> [photo.jpg ...]");
  process.exit(1);
}
const PRICE_IN = 0.1, PRICE_OUT = 0.5;
const device = randomBytes(32).toString("hex");

async function call(path, body) {
  const started = Date.now();
  const r = await fetch(base + path, {
    method: "POST",
    headers: { "x-app-key": appKey, "x-device": device, "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await r.json();
  return { status: r.status, data, ms: Date.now() - started };
}

function report(label, r) {
  if (r.status !== 200) return console.log(`${label.padEnd(34)} FAILED ${r.status} ${JSON.stringify(r.data).slice(0, 120)}`);
  const u = r.data.usage ?? {};
  // Cache reads are billed at a tenth of the input price, one-hour cache writes at twice.
  const cached = (u.cache_read_tokens ?? 0) * PRICE_IN * 0.1 + (u.cache_write_tokens ?? 0) * PRICE_IN * 2;
  const cost = (u.input_tokens * PRICE_IN + u.output_tokens * PRICE_OUT + cached) / 1e6;
  const note = (r.data.cached ? " (cache)" : "") + (u.cache_read_tokens ? `  [${u.cache_read_tokens} read from prompt cache]` : "") + (u.cache_write_tokens ? `  [${u.cache_write_tokens} written to prompt cache]` : "");
  console.log(`${label.padEnd(34)} in ${String(u.input_tokens).padStart(5)}  out ${String(u.output_tokens).padStart(5)}  $${cost.toFixed(5)}  ${(r.ms / 1000).toFixed(1)}s${note}`);
}

const context =
  "male, 30 years, 170 cm, 70 kg; goal: Giảm cân; daily target 1800 kcal; BMI 24.2; macro targets P 135g C 180g F 60g\n" +
  Array.from({ length: 7 }, (_, i) => `2026-10-0${i + 4}: in 1750 kcal (P 90g, C 210g, F 55g), out 2300 kcal, steps 6500`).join("\n") +
  "\nEaten today: Phở bò x1 (Sáng), Cơm trắng x1 (Trưa), Cá kho tộ x1 (Trưa)";

report("estimate  (bún chả)", await call("/v1/estimate", { name: "bún chả" }));
report("estimate  (same dish again)", await call("/v1/estimate", { name: "Bún chả" }));
report("chat      (one question)", await call("/v1/chat", { context, history: [{ role: "user", text: "Tối nay tôi nên ăn gì?" }] }));
for (const file of photos) {
  report(`photo     (${file.split(/[\\/]/).pop()})`, await call("/v1/photo", { image: readFileSync(file).toString("base64"), context: "male, 30 years; goal: Giảm cân" }));
}
