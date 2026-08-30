/**
  Adds the SITE_ADMIN role and the site_config table.

  SITE_ADMIN gates the home-page Admin button and the new /admin/site-config page,
  where a site admin edits DB-backed configuration (Google Maps + Twilio credentials)
  that previously lived only in environment variables.

  site_config is a key/value store: config_key is unique (one row per setting) with a
  synthetic id primary key. Secret values (API keys) are stored encrypted by the
  application; non-secret values (Twilio SID, from-number) are stored as-is. The table
  ships empty — a site admin must populate it before those features work.
 */

insert into wss_user_role(name) values ('SITE_ADMIN');

create table site_config(
  id serial primary key,
  config_key varchar(64) not null unique,
  config_value text not null
);
alter table site_config owner to wnc_helene;
