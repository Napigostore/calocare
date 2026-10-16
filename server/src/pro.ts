// CaloCare Pro: checks a purchase with the store it came from and remembers, per device, until when it is paid.
//  - App Store: the app sends the signed transaction (JWS) StoreKit gives it. Its certificate chain must end
//    in Apple's root certificate, so it needs no key from App Store Connect.
//  - Google Play: the app sends the purchase token; the server asks the Play Developer API about it with a
//    service account (secrets GOOGLE_SA_EMAIL and GOOGLE_SA_KEY).

export interface ProEnv {
  DB: D1Database;
  GOOGLE_SA_EMAIL?: string;
  GOOGLE_SA_KEY?: string;
  /** Tests only: a different root certificate and a fake Google API. */
  APPLE_ROOT_B64?: string;
  GOOGLE_API_BASE?: string;
}

export const PACKAGE = "com.calocare.app";
export const BUNDLE_ID = "com.calocare.app";
/** Play: one subscription with a monthly and a yearly base plan. App Store: two products in one group. */
export const PLAY_SUBSCRIPTION = "calocare_pro";
export const APPLE_PRODUCTS = ["calocare.pro.monthly", "calocare.pro.yearly"];
/** One purchase can unlock a few devices of the same person (phone, tablet, reinstall), not a whole group chat. */
export const MAX_DEVICES_PER_PURCHASE = 5;

// Apple Root CA - G3, from https://www.apple.com/certificateauthority/AppleRootCA-G3.cer
// SHA-256 63343ABFB89A6A03EBB57E9B3F5FA7BE7C4F5C756F3017B3A8C488C3653E9179
const APPLE_ROOT_G3 =
  "MIICQzCCAcmgAwIBAgIILcX8iNLFS5UwCgYIKoZIzj0EAwMwZzEbMBkGA1UEAwwSQXBwbGUgUm9vdCBDQSAtIEczMSYwJAYDVQQLDB1BcHBsZSBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwHhcNMTQwNDMwMTgxOTA2WhcNMzkwNDMwMTgxOTA2WjBnMRswGQYDVQQDDBJBcHBsZSBSb290IENBIC0gRzMxJjAkBgNVBAsMHUFwcGxlIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswCQYDVQQGEwJVUzB2MBAGByqGSM49AgEGBSuBBAAiA2IABJjpLz1AcqTtkyJygRMc3RCV8cWjTnHcFBbZDuWmBSp3ZHtfTjjTuxxEtX/1H7YyYl3J6YRbTzBPEVoA/VhYDKX1DyxNB0cTddqXl5dvMVztK517IDvYuVTZXpmkOlEKMaNCMEAwHQYDVR0OBBYEFLuw3qFYM4iapIqZ3r6966/ayySrMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMAoGCCqGSM49BAMDA2gAMGUCMQCD6cHEFl4aXTQY2e3v9GwOAEZLuN+yRhHFD/3meoyhpmvOwgPUnPWTxnS4at+qIxUCMG1mihDK1A3UT82NQz60imOlM27jbdoXt2QfyFMm+YhidDkLF1vLUagM6BgD56KyKA==";

export class ProError extends Error {
  constructor(readonly code: string, message: string) {
    super(message);
  }
}

// ------------------------------------------------------------------ bytes

const b64 = (s: string) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
const b64url = (s: string) => b64(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4));
const toB64url = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const utf8 = (s: string) => new TextEncoder().encode(s);
const equal = (a: Uint8Array, b: Uint8Array) => a.length === b.length && a.every((x, i) => x === b[i]);
const contains = (hay: Uint8Array, needle: number[]) => {
  outer: for (let i = 0; i + needle.length <= hay.length; i++) {
    for (let j = 0; j < needle.length; j++) if (hay[i + j] !== needle[j]) continue outer;
    return true;
  }
  return false;
};

/** One DER element: its tag, where its content starts and where the element ends. */
interface Der {
  tag: number;
  start: number; // first byte of the element
  body: number; // first byte of its content
  end: number; // one past its last byte
}

function der(bytes: Uint8Array, at: number): Der {
  const tag = bytes[at];
  let len = bytes[at + 1];
  let body = at + 2;
  if (len & 0x80) {
    const n = len & 0x7f;
    len = 0;
    for (let i = 0; i < n; i++) len = len * 256 + bytes[at + 2 + i];
    body += n;
  }
  if (body + len > bytes.length) throw new ProError("bad_certificate", "Malformed certificate");
  return { tag, start: at, body, end: body + len };
}

function children(bytes: Uint8Array, parent: Der): Der[] {
  const out: Der[] = [];
  for (let at = parent.body; at < parent.end; ) {
    const d = der(bytes, at);
    out.push(d);
    at = d.end;
  }
  return out;
}

const OID = {
  p256: [0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07],
  p384: [0x06, 0x05, 0x2b, 0x81, 0x04, 0x00, 0x22],
  sha256: [0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x04, 0x03, 0x02],
  sha384: [0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x04, 0x03, 0x03],
  // Marker extensions Apple puts in its App Store receipt-signing certificates.
  appleLeaf: [0x06, 0x0a, 0x2a, 0x86, 0x48, 0x86, 0xf7, 0x63, 0x64, 0x06, 0x0b, 0x01],
  appleIntermediate: [0x06, 0x0a, 0x2a, 0x86, 0x48, 0x86, 0xf7, 0x63, 0x64, 0x06, 0x02, 0x01],
};

/** Bytes WebCrypto accepts (backed by a plain ArrayBuffer). */
type Bytes = Uint8Array<ArrayBuffer>;

interface Cert {
  raw: Bytes;
  tbs: Bytes;
  signatureHash: "SHA-256" | "SHA-384";
  signature: Bytes; // DER-encoded ECDSA (r, s)
  spki: Bytes;
  curve: "P-256" | "P-384";
}

function parseCert(raw: Bytes): Cert {
  const top = der(raw, 0);
  const [tbsEl, algEl, sigEl] = children(raw, top);
  const tbsParts = children(raw, tbsEl);
  // TBSCertificate: [0] version, serial, signature, issuer, validity, subject, subjectPublicKeyInfo, …
  const spkiEl = tbsParts[tbsParts[0].tag === 0xa0 ? 6 : 5];
  const spki = raw.slice(spkiEl.start, spkiEl.end);
  const alg = raw.slice(algEl.start, algEl.end);
  const curve = contains(spki, OID.p384) ? "P-384" : contains(spki, OID.p256) ? "P-256" : null;
  const signatureHash = contains(alg, OID.sha384) ? "SHA-384" : contains(alg, OID.sha256) ? "SHA-256" : null;
  if (!curve || !signatureHash) throw new ProError("bad_certificate", "Unsupported certificate algorithm");
  // BIT STRING: the first content byte counts unused bits.
  return { raw, tbs: raw.slice(tbsEl.start, tbsEl.end), signatureHash, signature: raw.slice(sigEl.body + 1, sigEl.end), spki, curve };
}

/** ECDSA signatures in certificates are DER (r, s); WebCrypto wants r and s side by side at the curve's size. */
function rawSignature(sig: Bytes, size: number): Bytes {
  const seq = der(sig, 0);
  const [r, s] = children(sig, seq);
  const out = new Uint8Array(size * 2);
  for (const [i, el] of [r, s].entries()) {
    let v = sig.slice(el.body, el.end);
    while (v.length > size && v[0] === 0) v = v.slice(1);
    out.set(v, (i + 1) * size - v.length);
  }
  return out;
}

const keyOf = (c: Cert) => crypto.subtle.importKey("spki", c.spki, { name: "ECDSA", namedCurve: c.curve }, false, ["verify"]);

/** True when `child` was signed by `parent`'s key. */
async function signedBy(child: Cert, parent: Cert): Promise<boolean> {
  const size = parent.curve === "P-384" ? 48 : 32;
  return crypto.subtle.verify({ name: "ECDSA", hash: child.signatureHash }, await keyOf(parent), rawSignature(child.signature, size), child.tbs);
}

// ------------------------------------------------------------------ App Store

export interface AppleTransaction {
  bundleId: string;
  productId: string;
  originalTransactionId: string;
  transactionId: string;
  expiresDate?: number;
  revocationDate?: number;
  environment?: string;
}

/** Checks a StoreKit 2 signed transaction and returns its contents. Throws when anything does not hold. */
export async function verifyAppleTransaction(jws: string, env: ProEnv): Promise<AppleTransaction> {
  const parts = jws.split(".");
  if (parts.length !== 3) throw new ProError("bad_purchase", "Not a signed transaction");
  const header = JSON.parse(new TextDecoder().decode(b64url(parts[0])));
  if (header.alg !== "ES256" || !Array.isArray(header.x5c) || header.x5c.length !== 3) throw new ProError("bad_purchase", "Unexpected signature format");
  const [leaf, intermediate, root] = header.x5c.map((c: string) => parseCert(b64(c)));
  if (!equal(root.raw, b64(env.APPLE_ROOT_B64 || APPLE_ROOT_G3))) throw new ProError("bad_purchase", "Not signed by Apple");
  if (!contains(leaf.raw, OID.appleLeaf) || !contains(intermediate.raw, OID.appleIntermediate)) throw new ProError("bad_purchase", "Not an App Store certificate");
  if (!(await signedBy(intermediate, root)) || !(await signedBy(leaf, intermediate))) throw new ProError("bad_purchase", "Broken certificate chain");
  const ok = await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, await keyOf(leaf), b64url(parts[2]), utf8(parts[0] + "." + parts[1]));
  if (!ok) throw new ProError("bad_purchase", "Bad signature");
  const tx = JSON.parse(new TextDecoder().decode(b64url(parts[1]))) as AppleTransaction;
  if (tx.bundleId !== BUNDLE_ID || !APPLE_PRODUCTS.includes(tx.productId)) throw new ProError("bad_purchase", "Not a CaloCare Pro purchase");
  return tx;
}

// ------------------------------------------------------------------ Google Play

async function googleAccessToken(env: ProEnv): Promise<string> {
  if (!env.GOOGLE_SA_EMAIL || !env.GOOGLE_SA_KEY) throw new ProError("not_configured", "Google Play verification is not set up");
  const pem = env.GOOGLE_SA_KEY.replace(/\\n/g, "\n").replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const key = await crypto.subtle.importKey("pkcs8", b64(pem), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const now = Math.floor(Date.now() / 1000);
  const tokenUrl = (env.GOOGLE_API_BASE || "https://oauth2.googleapis.com") + "/token";
  const claims = { iss: env.GOOGLE_SA_EMAIL, scope: "https://www.googleapis.com/auth/androidpublisher", aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600 };
  const unsigned = toB64url(utf8(JSON.stringify({ alg: "RS256", typ: "JWT" }))) + "." + toB64url(utf8(JSON.stringify(claims)));
  const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, utf8(unsigned)));
  const response = await fetch(tokenUrl, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=${encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${unsigned}.${toB64url(signature)}`,
  });
  const body: any = await response.json().catch(() => ({}));
  if (!response.ok || !body.access_token) throw new ProError("store_unavailable", "Google sign-in failed");
  return body.access_token;
}

export interface PlayPurchase {
  expires: number;
  active: boolean;
  acknowledged: boolean;
  test: boolean;
}

/** Asks Google about a subscription purchase token and acknowledges it, which Play requires within three days. */
export async function verifyPlayPurchase(token: string, env: ProEnv): Promise<PlayPurchase> {
  const access = await googleAccessToken(env);
  const api = (env.GOOGLE_API_BASE || "https://androidpublisher.googleapis.com") + `/androidpublisher/v3/applications/${PACKAGE}/purchases`;
  const response = await fetch(`${api}/subscriptionsv2/tokens/${encodeURIComponent(token)}`, { headers: { authorization: `Bearer ${access}` } });
  if (response.status === 404 || response.status === 400) throw new ProError("bad_purchase", "Unknown purchase");
  if (!response.ok) throw new ProError("store_unavailable", `Google Play answered ${response.status}`);
  const sub: any = await response.json();
  const items: any[] = (sub.lineItems ?? []).filter((i: any) => i.productId === PLAY_SUBSCRIPTION);
  if (!items.length) throw new ProError("bad_purchase", "Not a CaloCare Pro purchase");
  const expires = Math.max(...items.map((i) => Date.parse(i.expiryTime) || 0));
  // Cancelled subscriptions stay paid until they expire; on hold and paused ones do not.
  const active = ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"].includes(sub.subscriptionState) && expires > Date.now();
  const acknowledged = sub.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED";
  if (active && !acknowledged) {
    await fetch(`${api}/subscriptions/${PLAY_SUBSCRIPTION}/tokens/${encodeURIComponent(token)}:acknowledge`, {
      method: "POST", headers: { authorization: `Bearer ${access}`, "content-type": "application/json" }, body: "{}",
    }).catch(() => undefined);
  }
  return { expires, active, acknowledged, test: !!sub.testPurchase };
}

// ------------------------------------------------------------------ storage

async function sha256Hex(text: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", utf8(text)));
  return [...digest].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Until when this device has Pro (ms since 1970), or 0. */
export async function proUntil(db: D1Database, device: string): Promise<number> {
  const row = await db.prepare(`SELECT expires FROM pro WHERE device = ?1`).bind(device).first<{ expires: number }>();
  return row && row.expires > Date.now() ? row.expires : 0;
}

/** Links a verified purchase to the device. `purchase` identifies the subscription across renewals. */
export async function grantPro(db: D1Database, device: string, platform: string, purchase: string, product: string, expires: number): Promise<number> {
  const ref = await sha256Hex(platform + ":" + purchase);
  const others = await db.prepare(`SELECT COUNT(*) AS n FROM pro WHERE ref = ?1 AND device != ?2`).bind(ref, device).first<{ n: number }>();
  if ((others?.n ?? 0) >= MAX_DEVICES_PER_PURCHASE) throw new ProError("too_many_devices", "This purchase is already in use on several devices");
  await db
    .prepare(
      `INSERT INTO pro (device, platform, ref, product, expires, updated) VALUES (?1, ?2, ?3, ?4, ?5, ?6)
       ON CONFLICT(device) DO UPDATE SET platform = ?2, ref = ?3, product = ?4, expires = ?5, updated = ?6`,
    )
    .bind(device, platform, ref, product, expires, Date.now())
    .run();
  return expires;
}

/** Checks a purchase sent by the app and links it to the device. Returns the new Pro expiry (0 when it has lapsed). */
export async function activate(env: ProEnv, device: string, body: any): Promise<number> {
  if (body?.platform === "ios" && typeof body.transaction === "string" && body.transaction.length < 20_000) {
    const tx = await verifyAppleTransaction(body.transaction, env);
    const expires = tx.revocationDate ? 0 : tx.expiresDate ?? 0;
    return grantPro(env.DB, device, "ios", tx.originalTransactionId, tx.productId, expires);
  }
  if (body?.platform === "android" && typeof body.token === "string" && body.token.length < 2_000) {
    const purchase = await verifyPlayPurchase(body.token, env);
    return grantPro(env.DB, device, "android", body.token, PLAY_SUBSCRIPTION, purchase.active ? purchase.expires : 0);
  }
  throw new ProError("bad_request", "Missing purchase");
}
