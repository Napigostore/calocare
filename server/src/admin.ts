/** The owner's page for the dish library: review what users submitted, fix numbers, publish. Data comes from /admin/api with the admin key. */
export const ADMIN_PAGE = `<!doctype html>
<html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>CaloCare – Thư viện món ăn</title>
<style>
  :root { --bg:#f6f8f4; --card:#fff; --text:#1b1f1c; --muted:#5b635c; --line:#dde3da; --green:#1f9d61; --red:#d32f2f; --amber:#b26a00; }
  @media (prefers-color-scheme: dark) { :root { --bg:#111512; --card:#1a1f1b; --text:#e3e7e2; --muted:#a9b2aa; --line:#353c36; } }
  * { box-sizing: border-box; }
  body { font: 15px/1.45 system-ui, sans-serif; margin: 0; background: var(--bg); color: var(--text); }
  header { padding: 16px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
  h1 { font-size: 20px; margin: 0; color: var(--green); flex: 1; }
  .tabs { display: flex; gap: 8px; padding: 0 16px; flex-wrap: wrap; }
  button, input, select { font: inherit; color: inherit; }
  button { border: 1px solid var(--line); background: var(--card); border-radius: 999px; padding: 6px 14px; cursor: pointer; }
  button.on, button.primary { background: var(--green); color: #fff; border-color: var(--green); }
  button.danger { color: var(--red); }
  input, select { border: 1px solid var(--line); background: var(--card); border-radius: 8px; padding: 6px 8px; width: 100%; }
  main { padding: 16px; display: grid; gap: 12px; }
  .dish { background: var(--card); border: 1px solid var(--line); border-radius: 14px; padding: 12px; display: grid; gap: 8px; }
  .row { display: grid; gap: 8px; grid-template-columns: repeat(auto-fit, minmax(90px, 1fr)); }
  .row.wide { grid-template-columns: 70px 2fr 2fr; }
  label { font-size: 12px; color: var(--muted); display: grid; gap: 2px; }
  .meta { font-size: 13px; color: var(--muted); }
  .bad { color: var(--red); font-size: 13px; }
  .actions { display: flex; gap: 8px; flex-wrap: wrap; }
  #msg { padding: 0 16px; color: var(--amber); min-height: 22px; }
  @media (max-width: 560px) { .row.wide { grid-template-columns: 1fr; } }
</style></head><body>
<header><h1>Thư viện món ăn CaloCare</h1><input id="key" type="password" placeholder="Admin key" style="max-width:220px"><button onclick="saveKey()">Vào</button></header>
<div class="tabs">
  <button data-tab="pending">Chờ duyệt</button><button data-tab="asked">Được hỏi nhiều</button>
  <button data-tab="published">Đã công bố</button><button data-tab="rejected">Đã từ chối</button><button data-tab="new">＋ Thêm món</button>
  <input id="q" placeholder="Lọc theo tên…" style="max-width:220px" oninput="render()">
</div>
<div id="msg"></div><main id="list"></main>
<script>
const COURSES = {soup:"Canh / khai vị", veg:"Rau", protein:"Đạm", main:"Món chính", fruit:"Trái cây", dessert:"Tráng miệng", drink:"Đồ uống", snack:"Ăn vặt"};
let tab = "pending", rows = [];
const $ = (s) => document.querySelector(s);
const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
$("#key").value = localStorage.getItem("calocare-admin-key") || "";
function saveKey() { localStorage.setItem("calocare-admin-key", $("#key").value.trim()); load(); }
async function api(path, body) {
  const r = await fetch("/admin/api/" + path, { method: body ? "POST" : "GET", headers: { "x-admin-key": $("#key").value.trim(), "content-type": "application/json" }, body: body ? JSON.stringify(body) : undefined });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(data.message || ("Lỗi " + r.status));
  return data;
}
function say(text) { $("#msg").textContent = text; }
async function load() {
  document.querySelectorAll("[data-tab]").forEach((b) => b.classList.toggle("on", b.dataset.tab === tab));
  if (tab === "new") { rows = [{ id: "", name: "", emoji: "🍽️", serving: "1 phần", kcal: "", protein_g: "", carbs_g: "", fat_g: "", course: "main", tags: [], region: "vn", fresh: true }]; return render(); }
  try { say("Đang tải…"); rows = (await api(tab === "asked" ? "asked" : "dishes?status=" + tab)).items; say(rows.length ? "" : "Không có món nào ở mục này."); render(); }
  catch (e) { say(e.message); rows = []; render(); }
}
function card(d, i) {
  const macro = Math.round(4 * (+d.protein_g || 0) + 4 * (+d.carbs_g || 0) + 9 * (+d.fat_g || 0));
  const off = d.kcal && macro && Math.abs(d.kcal - macro) > Math.max(30, macro / 4);
  const meta = tab === "asked" ? "Người dùng hỏi “" + esc(d.asked) + "” " + d.hits + " lần · số liệu do AI ước tính"
    : d.fresh ? "Món mới do bạn nhập" : "Nguồn: " + ({seed:"bảng gốc", users:"người dùng gửi", admin:"bạn nhập"}[d.source] || "") + (d.devices ? " · " + d.devices + " thiết bị" : "");
  return '<div class="dish" data-i="' + i + '"><div class="meta">' + meta + '</div>' +
    '<div class="row wide"><label>Biểu tượng<input name="emoji" value="' + esc(d.emoji) + '"></label><label>Tên món<input name="name" value="' + esc(d.name) + '"></label><label>Khẩu phần<input name="serving" value="' + esc(d.serving) + '"></label></div>' +
    '<div class="row"><label>kcal<input name="kcal" type="number" value="' + esc(d.kcal) + '"></label><label>Đạm (g)<input name="protein_g" type="number" step="0.1" value="' + esc(d.protein_g) + '"></label>' +
    '<label>Bột (g)<input name="carbs_g" type="number" step="0.1" value="' + esc(d.carbs_g) + '"></label><label>Béo (g)<input name="fat_g" type="number" step="0.1" value="' + esc(d.fat_g) + '"></label>' +
    '<label>Nhóm<select name="course">' + Object.entries(COURSES).map(([k, v]) => '<option value="' + k + '"' + (d.course === k ? " selected" : "") + '>' + v + '</option>').join("") + '</select></label></div>' +
    '<label>Nhãn (cách nhau bằng dấu cách: peanut shellfish fish milk egg gluten soy nuts sesame high_sugar high_gi high_carb high_sodium high_purine high_satfat fried meat pork animal alcohol)<input name="tags" value="' + esc((d.tags || []).join(" ")) + '"></label>' +
    (off ? '<div class="bad">Calo chưa khớp: đạm, bột, béo cộng lại ≈ ' + macro + ' kcal.</div>' : '') +
    '<div class="actions"><button class="primary" onclick="save(' + i + ',\\'published\\')">' + (tab === "published" ? "Lưu" : "Duyệt và công bố") + '</button>' +
    (tab === "published" || tab === "new" || tab === "asked" ? "" : '<button onclick="save(' + i + ',\\'pending\\')">Lưu, chưa công bố</button>') +
    (tab === "new" || tab === "asked" ? "" : '<button class="danger" onclick="save(' + i + ',\\'rejected\\')">' + (tab === "published" ? "Gỡ khỏi thư viện" : "Từ chối") + '</button>') + '</div></div>';
}
function render() {
  const q = $("#q").value.trim().toLowerCase();
  $("#list").innerHTML = rows.map((d, i) => (!q || (d.name || "").toLowerCase().includes(q) ? card(d, i) : "")).join("");
}
async function save(i, status) {
  const el = document.querySelector('.dish[data-i="' + i + '"]'), d = rows[i], body = { id: tab === "asked" ? "" : d.id, region: d.region || "vn", status };
  el.querySelectorAll("input, select").forEach((f) => (body[f.name] = f.value));
  try { await api("dish", body); say("Đã lưu “" + body.name + "”."); load(); } catch (e) { say(e.message); }
}
document.querySelectorAll("[data-tab]").forEach((b) => (b.onclick = () => { tab = b.dataset.tab; load(); }));
if ($("#key").value) load(); else say("Nhập admin key để bắt đầu.");
</script></body></html>`;
