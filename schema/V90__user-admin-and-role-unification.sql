/**
  Adds the USER_ADMIN role and an optional display name for users, then makes
  wss_user / wss_user_roles the single source of truth for user roles.

  Previously DRIVER and SITE_MANAGER were derived on-the-fly from the driver and
  site-contact tables. This migration backfills those as explicit wss_user_roles
  rows so the (new) user-management UI can read and edit every role in one place.
 */

/** Optional display name, editable in the user-management UI. */
alter table wss_user add column name varchar(128);

/** Only USER_ADMINs may see and edit the user-management UI. */
insert into wss_user_role(name) values ('USER_ADMIN');

/** Seed the first admins: everyone who is currently a data admin. */
insert into wss_user_roles(wss_user_id, wss_user_role_id)
select distinct wur.wss_user_id, (select id from wss_user_role where name = 'USER_ADMIN')
from wss_user_roles wur
join wss_user_role r on r.id = wur.wss_user_role_id
where r.name = 'DATA_ADMIN'
on conflict (wss_user_id, wss_user_role_id) do nothing;

/**
  Normalized (digits-only) phones of everyone who used to derive a role from
  another table. wss_user.phone stores digits only and must be 10-11 long, so we
  filter to that here. src_name lets us fill in a display name where we have one.
 */
create temporary table _role_backfill as
select digits, min(src_name) as src_name
from (
  select regexp_replace(phone, '[^0-9]+', '', 'g') as digits, name as src_name
    from driver where phone is not null
  union all
  select regexp_replace(contact_number, '[^0-9]+', '', 'g'), contact_name
    from site where contact_number is not null
  union all
  select regexp_replace(og_contact_number, '[^0-9]+', '', 'g'), contact_name
    from site where og_contact_number is not null
  union all
  select regexp_replace(phone, '[^0-9]+', '', 'g'), name
    from additional_site_manager where phone is not null
) s
where length(digits) between 10 and 11
group by digits;

/** Ensure a user row exists for every such phone (login whitelist + role holder). */
insert into wss_user(phone, name)
select digits, src_name from _role_backfill
on conflict (phone) do nothing;

/** Fill in a display name for pre-existing users that had none. */
update wss_user u
set name = b.src_name
from _role_backfill b
where u.phone = b.digits and u.name is null and b.src_name is not null;

/** DRIVER role for every driver phone. */
insert into wss_user_roles(wss_user_id, wss_user_role_id)
select u.id, (select id from wss_user_role where name = 'DRIVER')
from wss_user u
where u.phone in (
  select regexp_replace(phone, '[^0-9]+', '', 'g') from driver where phone is not null
)
on conflict (wss_user_id, wss_user_role_id) do nothing;

/** SITE_MANAGER role for every site primary / og / additional contact phone. */
insert into wss_user_roles(wss_user_id, wss_user_role_id)
select u.id, (select id from wss_user_role where name = 'SITE_MANAGER')
from wss_user u
where u.phone in (
  select regexp_replace(contact_number, '[^0-9]+', '', 'g') from site where contact_number is not null
  union
  select regexp_replace(og_contact_number, '[^0-9]+', '', 'g') from site where og_contact_number is not null
  union
  select regexp_replace(phone, '[^0-9]+', '', 'g') from additional_site_manager where phone is not null
)
on conflict (wss_user_id, wss_user_role_id) do nothing;

drop table _role_backfill;
