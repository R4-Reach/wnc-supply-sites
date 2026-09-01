-- Auth tokens became non-expiring when V34 dropped valid_until, so a captured `auth` cookie was
-- valid forever server-side. Restore a bounded lifetime: new tokens set expires_at explicitly on
-- insert (LoginDao.generateAuthToken); backfill existing rows from their creation time to match the
-- 14-day cookie lifetime, so nothing stays valid indefinitely.
alter table wss_user_auth_key
  add column expires_at timestamptz not null default now() + interval '14 days';

update wss_user_auth_key set expires_at = date_created + interval '14 days';
