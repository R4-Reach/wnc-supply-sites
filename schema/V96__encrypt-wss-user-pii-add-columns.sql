/*
  Encrypt wss_user.name and wss_user.phone at rest -- phase 1 of a staged, two-deploy cutover.

  This migration only ADDS the encrypted columns and a phone blind index; it does not touch the
  plaintext columns, which stay authoritative. An app-side backfill (WssUserPiiBackfill) populates
  the new columns on startup, and every write now dual-writes plaintext + encrypted. A later
  migration -- once the backfill is proven -- swaps the unique/length constraints onto the new
  columns and drops the plaintext, completing the cutover.

  phone_hmac is a keyed HMAC (blind index) so exact-phone lookups keep working once phone is
  encrypted: the ciphertext is randomized per row and can't be matched in a WHERE clause. name gets
  no blind index -- it is only ever displayed and sorted, never matched.
*/

-- Full backup for rollback safety; dropped in a later migration once the cutover is proven.
create table wss_user_backup_v96 as select * from wss_user;

alter table wss_user add column phone_enc text;
alter table wss_user add column phone_hmac text;
alter table wss_user add column name_enc text;

-- Mirrors the existing unique(phone). A partial index skips the not-yet-backfilled rows (NULL
-- phone_hmac) so it can be created immediately; it becomes the enforced uniqueness once the
-- plaintext phone column is dropped in the follow-up migration.
create unique index wss_user_phone_hmac_key on wss_user(phone_hmac) where phone_hmac is not null;
