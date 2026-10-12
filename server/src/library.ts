import { FOODS } from "./dishes";

/**
 * The dish library: the app's own table of Vietnamese dishes, kept on the server so it can grow
 * without an app release. Dishes start as the seed table, users' new dishes arrive as candidates
 * ("pending"), and only what the owner approves in the admin page is published to every phone.
 */
export interface Dish {
  id: string;
  name: string;
  emoji: string;
  serving: string;
  kcal: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  course: string;
  tags: string[];
  region: string;
  status: "published" | "pending" | "rejected";
  source: "seed" | "users" | "admin";
  /** How many different devices submitted this dish. */
  devices: number;
  updated: number;
  alcohol?: boolean;
}

export interface Library {
  dishes: Dish[];
  names: string[];
  /** Published dishes by normalised name. */
  table: Map<string, Dish>;
}

export interface Nutrition {
  kcal: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  alcohol?: boolean;
}

const COURSES = ["soup", "veg", "protein", "main", "fruit", "dessert", "drink", "snack"];
const TAGS = [
  "peanut", "nuts", "shellfish", "fish", "milk", "egg", "gluten", "soy", "sesame",
  "high_sugar", "high_gi", "high_carb", "high_sodium", "high_purine", "high_satfat", "fried", "meat", "pork", "animal", "alcohol",
];

export function nameKey(name: string): string {
  return name.normalize("NFC").toLowerCase().replace(/\s+/g, " ").trim();
}

/**
 * Energy has to add up: 4 kcal per gram of protein and carbohydrate, 9 per gram of fat. Numbers
 * that break this (rice with no carbohydrate but 200 kcal) are not a correction, they are noise.
 */
export function physicallyPossible(n: Nutrition): boolean {
  const values = [n.kcal, n.protein_g, n.carbs_g, n.fat_g];
  if (values.some((v) => typeof v !== "number" || !Number.isFinite(v) || v < 0) || n.kcal > 3000) return false;
  const fromMacros = 4 * n.protein_g + 4 * n.carbs_g + 9 * n.fat_g;
  const slack = Math.max(30, 0.25 * fromMacros);
  // Alcohol carries energy that is in none of the three macros, so only the lower bound applies.
  return n.kcal >= fromMacros - slack && (n.alcohol === true || n.kcal <= fromMacros + slack);
}

function fromRow(r: any): Dish {
  const tags = String(r.tags ?? "").split(" ").filter(Boolean);
  return { ...r, tags, alcohol: tags.includes("alcohol") || undefined };
}

// Read on almost every request and changed only when the owner publishes, so a short in-memory copy is enough.
let memo: { at: number; library: Library } | null = null;

export function forgetLibrary() {
  memo = null;
}

export async function loadLibrary(db: D1Database): Promise<Library> {
  if (memo && Date.now() - memo.at < 60_000) return memo.library;
  const rows = await db.prepare(`SELECT * FROM dishes WHERE status = 'published' ORDER BY id`).all();
  let dishes = rows.results.map(fromRow);
  // A database that was never seeded still has to work: fall back to the table built into the code.
  if (dishes.length === 0) {
    dishes = FOODS.map((f, i) => ({
      id: `seed_${i}`, name: f.name, emoji: "🍽️", serving: "1 phần", kcal: f.kcal, protein_g: f.protein_g, carbs_g: f.carbs_g, fat_g: f.fat_g,
      course: "main", tags: f.alcohol ? ["alcohol"] : [], region: "all", status: "published" as const, source: "seed" as const, devices: 0, updated: 0, alcohol: f.alcohol,
    }));
  }
  const library = { dishes, names: dishes.map((d) => d.name), table: new Map(dishes.map((d) => [nameKey(d.name), d])) };
  memo = { at: Date.now(), library };
  return library;
}

/** What changed since the app last synchronised: dishes to add or replace, and ids to drop. */
export async function changesSince(db: D1Database, since: number) {
  const rows = await db.prepare(`SELECT * FROM dishes WHERE updated > ?1 ORDER BY updated`).bind(since).all();
  const all = rows.results.map(fromRow);
  const version = all.reduce((v, d) => Math.max(v, d.updated), since);
  const strip = ({ status, source, devices, updated, alcohol, ...dish }: Dish) => dish;
  return {
    version,
    dishes: all.filter((d) => d.status === "published").map(strip),
    // On a first sync there is nothing on the phone to remove.
    removed: since > 0 ? all.filter((d) => d.status !== "published").map((d) => d.id) : [],
  };
}

function cleanDish(body: any): Omit<Dish, "id" | "status" | "source" | "devices" | "updated"> | null {
  const name = typeof body?.name === "string" ? body.name.normalize("NFC").replace(/\s+/g, " ").trim() : "";
  const n = { kcal: Math.round(Number(body?.kcal)), protein_g: Number(body?.protein_g) || 0, carbs_g: Number(body?.carbs_g) || 0, fat_g: Number(body?.fat_g) || 0 };
  const tags: string[] = (Array.isArray(body?.tags) ? body.tags : String(body?.tags ?? "").split(/[\s,]+/)).filter((t: unknown) => typeof t === "string" && TAGS.includes(t));
  if (name.length < 2 || name.length > 60 || !(n.kcal > 0) || !physicallyPossible({ ...n, alcohol: tags.includes("alcohol") })) return null;
  return {
    name, ...n, tags,
    emoji: typeof body?.emoji === "string" && body.emoji ? body.emoji.slice(0, 8) : "🍽️",
    serving: typeof body?.serving === "string" && body.serving.trim() ? body.serving.trim().slice(0, 160) : "1 phần",
    course: COURSES.includes(body?.course) ? body.course : "main",
    region: body?.region === "intl" || body?.region === "all" ? body.region : "vn",
  };
}

async function hashId(key: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(key)));
  return "u_" + [...digest.slice(0, 8)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * A dish one user created on their phone, offered as a candidate for the library. It only ever
 * becomes "pending": nothing a user sends reaches other users until the owner approves it.
 */
export async function submitDish(db: D1Database, device: string, body: any, day: string): Promise<void> {
  const dish = cleanDish(body);
  if (!dish) return;
  const key = nameKey(dish.name);
  const library = await loadLibrary(db);
  if (library.table.has(key)) return;
  const sent = await db.prepare(`SELECT COUNT(*) AS n FROM dish_votes WHERE device = ?1 AND day = ?2`).bind(device, day).first<{ n: number }>();
  if ((sent?.n ?? 0) >= 30) return;
  await db
    .prepare(
      `INSERT INTO dish_votes (key, device, day, kcal, protein_g, carbs_g, fat_g, serving) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
       ON CONFLICT(key, device) DO UPDATE SET day = ?3, kcal = ?4, protein_g = ?5, carbs_g = ?6, fat_g = ?7, serving = ?8`,
    )
    .bind(key, device, day, dish.kcal, dish.protein_g, dish.carbs_g, dish.fat_g, dish.serving)
    .run();
  // The candidate shows the average of what its submitters entered, and how many of them there are.
  const avg = await db
    .prepare(`SELECT COUNT(*) AS devices, AVG(kcal) AS kcal, AVG(protein_g) AS p, AVG(carbs_g) AS c, AVG(fat_g) AS f FROM dish_votes WHERE key = ?1`)
    .bind(key)
    .first<{ devices: number; kcal: number; p: number; c: number; f: number }>();
  const id = await hashId(key);
  await db
    .prepare(
      `INSERT INTO dishes (id, name, emoji, serving, kcal, protein_g, carbs_g, fat_g, course, tags, region, status, source, devices, updated)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, 'pending', 'users', ?12, ?13)
       ON CONFLICT(id) DO UPDATE SET devices = ?12,
         kcal = CASE WHEN status = 'pending' THEN ?5 ELSE kcal END, protein_g = CASE WHEN status = 'pending' THEN ?6 ELSE protein_g END,
         carbs_g = CASE WHEN status = 'pending' THEN ?7 ELSE carbs_g END, fat_g = CASE WHEN status = 'pending' THEN ?8 ELSE fat_g END`,
    )
    .bind(id, dish.name, dish.emoji, dish.serving, Math.round(avg!.kcal), round1(avg!.p), round1(avg!.c), round1(avg!.f), dish.course, dish.tags.join(" "), dish.region, avg!.devices, Date.now())
    .run();
}

const round1 = (v: number) => Math.round(v * 10) / 10;

// ------------------------------------------------------------------ owner's side

export async function adminList(db: D1Database, status: string) {
  const rows = await db.prepare(`SELECT * FROM dishes WHERE status = ?1 ORDER BY devices DESC, updated DESC LIMIT 500`).bind(status).all();
  return rows.results.map(fromRow);
}

/** Dish names users asked the AI about most, with the AI's numbers: the demand the library does not cover yet. */
export async function adminAsked(db: D1Database) {
  const library = await loadLibrary(db);
  const rows = await db.prepare(`SELECT name, result, hits FROM estimates ORDER BY hits DESC LIMIT 200`).all<{ name: string; result: string; hits: number }>();
  return rows.results.flatMap((r) => {
    if (library.table.has(nameKey(r.name))) return [];
    try {
      const item = JSON.parse(r.result).items[0];
      return [{ asked: r.name, hits: r.hits, name: item.name, emoji: item.emoji, serving: item.portion, kcal: item.kcal, protein_g: item.protein_g, carbs_g: item.carbs_g, fat_g: item.fat_g, course: item.course, tags: item.tags }];
    } catch {
      return [];
    }
  });
}

/** Creates or edits a dish and sets its status. Returns an error message for the page, or null. */
export async function adminSave(db: D1Database, body: any): Promise<string | null> {
  const status = body?.status;
  if (!["published", "pending", "rejected"].includes(status)) return "Trạng thái không hợp lệ";
  const dish = cleanDish(body);
  if (!dish) return "Số liệu không hợp lệ: tên 2–60 ký tự, calo > 0 và phải khớp với đạm, bột, béo (4-4-9).";
  const id = typeof body?.id === "string" && body.id ? body.id : await hashId(nameKey(dish.name));
  if (status === "published") {
    const clash = await db.prepare(`SELECT id FROM dishes WHERE status = 'published' AND id != ?1 AND lower(name) = lower(?2)`).bind(id, dish.name).first();
    if (clash) return "Đã có món cùng tên trong thư viện.";
  }
  await db
    .prepare(
      `INSERT INTO dishes (id, name, emoji, serving, kcal, protein_g, carbs_g, fat_g, course, tags, region, status, source, devices, updated)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, 'admin', 0, ?13)
       ON CONFLICT(id) DO UPDATE SET name = ?2, emoji = ?3, serving = ?4, kcal = ?5, protein_g = ?6, carbs_g = ?7, fat_g = ?8, course = ?9, tags = ?10, region = ?11, status = ?12, updated = ?13`,
    )
    .bind(id, dish.name, dish.emoji, dish.serving, dish.kcal, dish.protein_g, dish.carbs_g, dish.fat_g, dish.course, dish.tags.join(" "), dish.region, status, Date.now())
    .run();
  forgetLibrary();
  return null;
}
