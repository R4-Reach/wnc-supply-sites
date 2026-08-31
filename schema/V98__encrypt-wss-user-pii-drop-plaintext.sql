/*
  Encrypt wss_user.name and wss_user.phone at rest -- phase 2, the cutover. Phase 1 (V96) added the
  encrypted columns, made every write dual-write them, and backfilled existing rows; by the time this
  migration runs (a later deploy) every row has phone_enc and phone_hmac populated, plus name_enc
  wherever a name exists. This flips the encrypted columns to authoritative -- enforcing NOT NULL and
  the phone uniqueness on them -- and drops the plaintext phone and name.

  MUST NOT be deployed until phase 1's backfill has completed in production, or the NOT NULL below
  fails on un-backfilled rows.

  The plaintext backup table wss_user_backup_v96 is intentionally kept as a safety net; a later
  migration drops it once the cutover is proven in production.
*/

alter table wss_user alter column phone_enc set not null;
alter table wss_user alter column phone_hmac set not null;

-- phone_hmac now carries the uniqueness that unique(phone) used to. Replace the phase-1 partial index
-- (which only covered backfilled rows) with a full unique constraint, now that every row has a value.
drop index wss_user_phone_hmac_key;
alter table wss_user add constraint wss_user_phone_hmac_key unique (phone_hmac);

-- Drop the plaintext columns. This also drops the unique(phone) constraint and the length check that
-- rode on phone. name_enc stays nullable, mirroring the nullable plaintext name it replaces.
alter table wss_user drop column phone;
alter table wss_user drop column name;
