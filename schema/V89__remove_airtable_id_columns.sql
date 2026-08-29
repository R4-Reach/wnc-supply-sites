-- Retire the Airtable integration: drop the airtable_id linkage columns from
-- every table that carried them, and move the driver and delivery identities
-- onto the shared public wss_id sequence (the key their upserts now key on).

-- Neither driver nor delivery had a wss_id. Add one from the shared sequence,
-- backfill existing rows, then make it the not-null unique identity before
-- airtable_id is dropped.
alter table driver add column wss_id integer;
update driver set wss_id = nextval('wss_id') where wss_id is null;
alter table driver alter column wss_id set default nextval('wss_id');
alter table driver alter column wss_id set not null;
alter table driver add constraint driver_wss_id_uk unique (wss_id);

alter table delivery add column wss_id integer;
update delivery set wss_id = nextval('wss_id') where wss_id is null;
alter table delivery alter column wss_id set default nextval('wss_id');
alter table delivery alter column wss_id set not null;
alter table delivery add constraint delivery_wss_id_uk unique (wss_id);

-- Dropping the column also drops driver_airtable_id_uk.
alter table driver drop column airtable_id;
alter table delivery drop column airtable_id;
alter table site drop column airtable_id;
alter table item drop column airtable_id;
