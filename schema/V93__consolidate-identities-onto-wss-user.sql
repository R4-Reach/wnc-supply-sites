/**
  Consolidate the scattered phone-bearing identities onto wss_user.

  Previously a site's managers were expressed three ways -- site.contact_number (primary),
  site.og_contact_number (permanent original), and the additional_site_manager table -- each
  storing a phone that duplicated a wss_user. This migration makes wss_user the single identity
  and resurrects wss_user_sites (created back in V31 but never used) as the single source of truth
  for who manages a site. Sites keep two foreign keys to mark the displayed primary contact and the
  permanent original creator; drivers gain a foreign key to their user. The redundant phone/name
  columns and the additional_site_manager table are then dropped.

  Phones were canonicalized to 11 digits in V92, so the backfill joins match by phone equality.
 */

-- Foreign keys onto wss_user. The site contacts are nullable: a few sites have no recorded
-- primary or original contact (their contact number was blank).
alter table site add column primary_contact_wss_user_id integer references wss_user(id);
alter table site add column og_contact_wss_user_id integer references wss_user(id);
alter table driver add column wss_user_id integer references wss_user(id);

update site s set primary_contact_wss_user_id = u.id
  from wss_user u where u.phone = s.contact_number;
update site s set og_contact_wss_user_id = u.id
  from wss_user u where u.phone = s.og_contact_number;
update driver d set wss_user_id = u.id
  from wss_user u where u.phone = d.phone;

-- Carry any name that only lived on the old rows onto the user before those columns are dropped.
update wss_user u set name = s.contact_name
  from site s where s.contact_number = u.phone and u.name is null and s.contact_name is not null;
update wss_user u set name = d.name
  from driver d where d.phone = u.phone and u.name is null and d.name is not null;
update wss_user u set name = m.name
  from additional_site_manager m
  where m.phone = u.phone and u.name is null and m.name is not null;

-- wss_user_sites becomes the manager-membership table: primary contacts, original contacts, and
-- every additional site manager collapse into it.
insert into wss_user_sites(wss_user_id, site_id)
  select primary_contact_wss_user_id, id from site where primary_contact_wss_user_id is not null
  union
  select og_contact_wss_user_id, id from site where og_contact_wss_user_id is not null
  union
  select u.id, m.site_id
    from additional_site_manager m
    join wss_user u on u.phone = m.phone
on conflict (wss_user_id, site_id) do nothing;

-- Every driver maps to a user (driver.phone was unique and mirrored into wss_user); make the link
-- mandatory and unique.
alter table driver alter column wss_user_id set not null;
alter table driver add constraint driver_wss_user_id_uk unique (wss_user_id);

-- Retire the redundant identity storage.
drop table additional_site_manager;
alter table site drop column contact_name;
alter table site drop column contact_number;
alter table site drop column og_contact_number;
alter table driver drop column phone;
alter table driver drop column name;
