-- A delivery now references its dispatcher and driver by their wss_user identity instead of copying
-- their name/phone onto the row as free text. Name and phone are derived from that record at read
-- time, so a later phone change is reflected everywhere rather than going stale on old rows.
--
-- The legacy dispatcher_name/dispatcher_number/driver_name/driver_number columns are kept for
-- deliveries created before this change; reads fall back to them when the reference is null.
alter table delivery
  add column dispatcher_wss_user_id bigint references wss_user(id),
  add column driver_wss_user_id bigint references wss_user(id);
