/*
  Hand wss_user_backup_v96 to the app role. V96 created it with CREATE TABLE AS, which -- unlike
  every other table in this schema -- left it owned by the migration superuser, so the app role
  wnc_helene can neither read nor drop it. The PII re-encryption remediation (WssUserPiiReencrypt)
  reads the surviving plaintext phone/name from this table on startup, and the eventual cleanup that
  drops it will run as wnc_helene too, so bring it in line with the rest of the schema.
*/

alter table wss_user_backup_v96 owner to wnc_helene;
