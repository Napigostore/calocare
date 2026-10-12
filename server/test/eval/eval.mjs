// Measures how often the server names a dish correctly, on the labelled photos fetched by fetch_images.py.
// Spends one real photo analysis per image (about $0.0001–0.0006 each).
//
//   node test/eval/eval.mjs https://calocare-api.<subdomain>.workers.dev <appKey> [image folder, default "images"]
//
// A photo counts as:
//   correct   the labelled dish is among the names the server returned
//   in top 3  ... or among the alternatives it offered (one tap away for the user)
// Results are written to results-<folder>.json so runs can be compared after a change.
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { randomBytes } from "node:crypto";

const here = new URL(".", import.meta.url);
const [base, appKey, folder = "images"] = process.argv.slice(2);
if (!base || !appKey) {
  console.error("usage: node test/eval/eval.mjs <server url> <app key> [image folder]");
  process.exit(1);
}

const fold = (s) => s.toLowerCase().replace(/đ/g, "d").normalize("NFD").replace(/\p{M}+/gu, "").replace(/[^a-z0-9]+/g, " ").trim();
const excluded = new Set(existsSync(new URL("exclude.txt", here)) ? readFileSync(new URL("exclude.txt", here), "utf8").split(/\r?\n/).filter(Boolean) : []);
const photos = JSON.parse(readFileSync(new URL("manifest.json", here), "utf8")).filter((p) => !excluded.has(p.file));

// Each device may send 30 photos a day while on trial, so the run is spread over several test devices.
const devices = Array.from({ length: Math.ceil(photos.length / 25) }, () => randomBytes(32).toString("hex"));

const results = [];
let spentIn = 0, spentOut = 0, cacheRead = 0, cacheWrite = 0, strongCost = 0, escalated = 0;
for (const [i, photo] of photos.entries()) {
  const image = readFileSync(new URL(`${folder}/${photo.file}`, here)).toString("base64");
  const r = await fetch(base + "/v1/photo", {
    method: "POST",
    headers: { "x-app-key": appKey, "x-device": devices[Math.floor(i / 25)], "content-type": "application/json" },
    // No habits in the context: this measures what the photo alone gives.
    body: JSON.stringify({ image, context: "adult; goal: Giữ cân" }),
  });
  const data = await r.json();
  if (r.status !== 200) {
    console.log(`${photo.file}: FAILED ${r.status} ${data.message ?? ""}`);
    results.push({ ...photo, error: r.status });
    continue;
  }
  const items = data.result.items ?? [];
  const names = items.map((it) => it.name);
  const alternatives = items.flatMap((it) => it.alternatives ?? []);
  const want = fold(photo.label);
  const correct = names.some((n) => fold(n) === want);
  const top3 = correct || alternatives.some((n) => fold(n) === want);
  spentIn += data.usage.input_tokens; spentOut += data.usage.output_tokens;
  cacheRead += data.usage.cache_read_tokens ?? 0; cacheWrite += data.usage.cache_write_tokens ?? 0;
  // Stronger model (Claude Sonnet 5.5 list prices: $2 in, $10 out, cache read $0.20, one-hour cache write $4 per million).
  const strong = data.usage.strong;
  if (strong) {
    escalated++;
    strongCost += (strong.input_tokens * 2 + strong.output_tokens * 10 + strong.cache_read_tokens * 0.2 + strong.cache_write_tokens * 4) / 1e6;
  }
  const minConfidence = items.length ? Math.min(...items.map((it) => it.confidence ?? 0)) : 0;
  results.push({ file: photo.file, label: photo.label, names, alternatives, correct, top3, minConfidence, escalated: !!strong });
  if (!correct) console.log(`${top3 ? "alt " : "MISS"}  ${photo.label.padEnd(26)} → ${names.join(", ") || "(không thấy món)"}${alternatives.length ? `   [hay là: ${alternatives.join(", ")}]` : ""}`);
}

const scored = results.filter((r) => !r.error);
const pct = (n) => `${((100 * n) / Math.max(scored.length, 1)).toFixed(0)}%`;
const correct = scored.filter((r) => r.correct).length;
const top3 = scored.filter((r) => r.top3).length;
const cost = (spentIn * 0.1 + spentOut * 0.5 + cacheRead * 0.01 + cacheWrite * 0.2) / 1e6 + strongCost;
console.log(`\n${scored.length} photos · correct ${correct} (${pct(correct)}) · correct or offered as alternative ${top3} (${pct(top3)}) · failed ${results.length - scored.length}`);
if (escalated) console.log(`escalated to the stronger model: ${escalated} of ${scored.length} photos (${pct(escalated)}), costing $${strongCost.toFixed(4)}`);
// The dollar figure below prices the first model's tokens as Claude Haiku 5.5.
console.log(`tokens: input ${spentIn}, output ${spentOut}, cache read ${cacheRead}, cache write ${cacheWrite}`);
console.log(`cost of this run ≈ $${cost.toFixed(4)} (${(cost / Math.max(scored.length, 1)).toFixed(5)} per photo)`);

const byDish = {};
for (const r of scored) (byDish[r.label] ??= []).push(r.correct);
const weak = Object.entries(byDish).filter(([, v]) => v.filter(Boolean).length < v.length / 2 + 0.01).map(([k, v]) => `${k} ${v.filter(Boolean).length}/${v.length}`);
if (weak.length) console.log(`weakest dishes: ${weak.join("; ")}`);
writeFileSync(new URL(`results-${folder}.json`, here), JSON.stringify({ date: new Date().toISOString(), folder, photos: scored.length, correct, top3, results }, null, 1));
