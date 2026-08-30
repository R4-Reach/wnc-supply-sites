/**
  Dispatch drivers grid: a configurable vehicle-type pick list, a driver's chosen type, and the
  DRIVER_ADMIN role.

  The DISPATCHER role already exists (V37). DRIVER_ADMIN is the read/write counterpart for the
  dispatch drivers page; DISPATCHER is read-only there except for the notes field.

  vehicle_type is a small reference list (like county/item) managed by a SITE_ADMIN on the site
  config page. A driver has at most one type. The foreign key deliberately has no ON DELETE action,
  so Postgres blocks removing a type while any driver still references it -- the admin UI checks for
  this first and reports it, and the constraint is the backstop.
 */
create table vehicle_type(
  id serial primary key,
  name varchar(64) not null unique
);
alter table vehicle_type owner to wnc_helene;

insert into vehicle_type(name) values
  ('Van'),
  ('Car'),
  ('SUV'),
  ('Pickup Truck'),
  ('Trailer');

alter table driver add column vehicle_type_id integer references vehicle_type(id);

insert into wss_user_role(name) values ('DRIVER_ADMIN');
