-- Promote description tags to first-class, admin-managed entities. Until now a tag existed only
-- as a free-text string on an item_tag row, populated by the (since-removed) Airtable webhook;
-- there was no way to create, rename, or delete a tag independently of item assignments. Introduce
-- a tag registry and repoint item_tag at it by id, so a rename is a single-row update and a tag can
-- exist with zero assignments.

create table tag(
  id serial primary key,
  name varchar(64) not null unique,
  date_created timestamptz not null default now()
);
alter table tag owner to wnc_helene;

-- Seed the registry from the tag names already in use.
insert into tag(name)
  select distinct tag_name from item_tag;

-- Repoint item_tag from the free-text name to a tag_id foreign key. on delete cascade lets a tag
-- deletion in the admin UI drop its assignments in one step.
alter table item_tag add column tag_id integer references tag(id) on delete cascade;
update item_tag set tag_id = tag.id from tag where tag.name = item_tag.tag_name;
alter table item_tag alter column tag_id set not null;

alter table item_tag drop constraint item_category_uk;
alter table item_tag add constraint item_tag_item_tag_uk unique(item_id, tag_id);
alter table item_tag drop column tag_name;
