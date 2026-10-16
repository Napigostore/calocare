-- One row per device (and one reserved row, device = '*', whose chats_day counts all calls today).
-- CaloCare Pro, per device: until when it is paid. ref is a hash of the store purchase, so one
-- subscription can be shared by a few devices of its owner but no more.
CREATE TABLE IF NOT EXISTS pro (
  device   TEXT PRIMARY KEY,
  platform TEXT NOT NULL,
  ref      TEXT NOT NULL,
  product  TEXT NOT NULL,
  expires  INTEGER NOT NULL,
  updated  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS pro_ref ON pro (ref);

CREATE TABLE IF NOT EXISTS usage (
  device       TEXT PRIMARY KEY,
  day          TEXT NOT NULL,
  photos_total INTEGER NOT NULL DEFAULT 0,
  chats_total  INTEGER NOT NULL DEFAULT 0,
  photos_day   INTEGER NOT NULL DEFAULT 0,
  chats_day    INTEGER NOT NULL DEFAULT 0
);

-- Shared cache of AI estimates for dishes looked up by name. Holds no user data.
CREATE TABLE IF NOT EXISTS estimates (
  name   TEXT PRIMARY KEY,
  result TEXT NOT NULL,
  hits   INTEGER NOT NULL DEFAULT 0
);

-- Learning from corrections. fix_votes holds one row per device and correction so each device counts once;
-- fixes is the tally; names counts how often the AI used a name. Dish names only: no photos, no profiles.
CREATE TABLE IF NOT EXISTS fix_votes (
  ai_name TEXT NOT NULL,
  chosen  TEXT NOT NULL,
  device  TEXT NOT NULL,
  day     TEXT NOT NULL,
  PRIMARY KEY (ai_name, chosen, device)
);
CREATE TABLE IF NOT EXISTS fixes (
  ai_name TEXT NOT NULL,
  chosen  TEXT NOT NULL,
  devices INTEGER NOT NULL DEFAULT 0,
  -- 1 = the two dishes are nutritionally far apart or could not be checked: offered as an alternative only.
  far     INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (ai_name, chosen)
);
CREATE TABLE IF NOT EXISTS names (
  ai_name TEXT PRIMARY KEY,
  seen    INTEGER NOT NULL DEFAULT 0
);

-- The dish library. status: published (sent to every phone) | pending (awaiting the owner's review) | rejected.
-- updated is a millisecond timestamp; phones ask for rows newer than the one they last saw.
CREATE TABLE IF NOT EXISTS dishes (
  id        TEXT PRIMARY KEY,
  name      TEXT NOT NULL,
  emoji     TEXT NOT NULL DEFAULT '🍽️',
  serving   TEXT NOT NULL DEFAULT '1 phần',
  kcal      INTEGER NOT NULL,
  protein_g REAL NOT NULL DEFAULT 0,
  carbs_g   REAL NOT NULL DEFAULT 0,
  fat_g     REAL NOT NULL DEFAULT 0,
  course    TEXT NOT NULL DEFAULT 'main',
  tags      TEXT NOT NULL DEFAULT '',
  region    TEXT NOT NULL DEFAULT 'vn',
  status    TEXT NOT NULL DEFAULT 'pending',
  source    TEXT NOT NULL DEFAULT 'users',
  devices   INTEGER NOT NULL DEFAULT 0,
  updated   INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS dishes_updated ON dishes (updated);
-- One row per device and candidate dish, so a dish counts each submitter once.
CREATE TABLE IF NOT EXISTS dish_votes (
  key       TEXT NOT NULL,
  device    TEXT NOT NULL,
  day       TEXT NOT NULL,
  kcal      INTEGER NOT NULL,
  protein_g REAL NOT NULL,
  carbs_g   REAL NOT NULL,
  fat_g     REAL NOT NULL,
  serving   TEXT NOT NULL,
  PRIMARY KEY (key, device)
);
