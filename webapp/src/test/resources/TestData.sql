delete from wss_user_auth_key;
delete from wss_user_pass_change_history;
delete from sms_passcode;
delete from wss_user_roles;
delete from wss_user;
delete from driver;

delete from volunteer_delivery_item;
delete from volunteer_delivery;

delete from site_distance_matrix;
delete from delivery_item;
delete from delivery_confirmation;
delete from delivery;
delete from site_item_audit;
delete from site_item;
delete from item_tag;
delete from item;
delete from site_audit_trail;
delete from additional_site_manager;
delete from site;
delete from county;

insert into county(name, state) values('Ashe', 'NC');
insert into county(name, state) values('Buncombe', 'NC');
insert into county(name, state) values('Los Angeles', 'CA');

insert into county(name, state) values('Watauga', 'NC');
insert into county(name, state) values('Sevier', 'TN');
insert into county(name, state) values('Halifax', 'VA');
insert into county(name) values('dummy') on conflict do nothing;

insert into max_supply_load(id, sort_order, name, default_selection)
values( -100, 25, 'test-value', false) on conflict do nothing;

insert into site(
  name,
  contact_name,
  contact_number,
  og_contact_number,
  address,
  city,
  county_id,
  website,
  site_type_id,
  accepting_donations,
  active,
  airtable_id,
  hours,
  facebook,
  wss_id,
  publicly_visible,
  distributing_supplies,
  max_supply_load_id,
  inactive_reason)
values (
        'site1',
        'contact me',
        '111',
        '111',
        'address1',
        'city1',
        (select id from county where name = 'Watauga' and state = 'NC'),
        'site1website',
        (select id from site_type where name = 'Distribution Center'),
        true,
        true ,
        -200,
        'our hours',
        'fb url',
        -10,
        true,
        true,
        (select id from max_supply_load where name = 'Car'),
        'inactive reason text'
       );


-- site2, in Buncombe county, not accepting donations
insert into site(name, address, city, county_id, accepting_donations, site_type_id, wss_id, max_supply_load_id,
                 contact_number, og_contact_number)
values ('site2', 'address2', 'city2', (select id from county where name = 'Buncombe'), false,
        (select id from site_type where name = 'Distribution Center'), -20, -100, '123', '123');

-- siteCA, in CA
insert into site(name, address, city, county_id, accepting_donations, site_type_id, wss_id, max_supply_load_id, contact_number, og_contact_number) values(
  'siteCA', 'address2', 'city2', (select id from county where name = 'Los Angeles'), false,
  (select id from site_type where name = 'Distribution Center'), -201, -100, '123', '123');

-- site3, in Buncombe county, not active
insert into site(name, address, city, county_id, active, site_type_id, max_supply_load_id, contact_number, og_contact_number) values(
'site3', 'address3', 'city2', (select id from county where name = 'Buncombe'), false,
(select id from site_type where name = 'Distribution Center'), -100, '222', '222');

-- create a delivery from site2 to site3
insert into delivery(
  airtable_id, from_site_id, to_site_id,
  delivery_status, target_delivery_date, dispatcher_name,
  dispatcher_number, driver_name, driver_number,
  driver_license_plates, public_url_key, dispatch_code, driver_code)
values(
        -1,
        (select id from site where name = 'site2'),
        (select id from site where name = 'site3'),
        'Creating Dispatch',
        to_date('2024-12-13', 'YYYY-MM-DD'),
        'dispatcher1',
        '555',
        'driver1',
        'call me anytime',
        'traveler',
       'BETA',
       'ATEB',
       'DRIV'
      );



-- site4, in Buncombe county, no items (but active), supply hub
insert into site(name, address, city, county_id, site_type_id, max_supply_load_id, contact_number, og_contact_number) values(
   'site4', 'address3', 'city2', (select id from county where name = 'Buncombe'),
   (select id from site_type where name = 'Supply Hub'), -100, '333', '333'
);
-- create a delivery from site3 to site4
insert into delivery(
  airtable_id, from_site_id, to_site_id,
  delivery_status, target_delivery_date, dispatcher_name,
  dispatcher_number, driver_name, driver_number,
  driver_license_plates, public_url_key, dispatch_code, driver_code)
values(
        -2,
        (select id from site where name = 'site3'),
        (select id from site where name = 'site4'),
        'Creating Dispatch',
        to_date('2024-12-13', 'YYYY-MM-DD'),
        'dispatcher1',
        '555',
        'driver1',
        'call me anytime',
        'traveler',
       'XKCD',
       'DCKX',
       'PPPP'
      );


-- create a delivery from site4 to site3
-- this is a  minimal 'data', everything that can be null should be null
insert into delivery(
  airtable_id, from_site_id, to_site_id, public_url_key, dispatch_code, driver_code)
values(
        -3,
        (select id from site where name = 'site3'),
        (select id from site where name = 'site4'),
       'ABCD',
       'DCBA',
       'IIII'
      );

-- site5, (no items & not active), name, address & details may be modified by tests,
-- data will not be stable.
insert into site(name, address, city, county_id, site_type_id, max_supply_load_id, contact_number, og_contact_number) values(
   'site5', 'address5', 'city5', (select id from county where name = 'Buncombe'),
   (select id from site_type where name = 'Distribution Center'), -100, 444, 444
);

insert into site(name, address, city, county_id, website, site_type_id, max_supply_load_id, contact_number, og_contact_number) values(
   'site6', 'address6', 'city6', (select id from county where name = 'Watauga'), 'site6website',
   (select id from site_type where name = 'Distribution Center'), -100, 555, 555
);


insert into item(name, wss_id) values('water', -40);
insert into item(name, wss_id) values('soap', -30);
insert into item(name, wss_id) values('gloves', -50);
insert into item(name, wss_id) values('used clothes', -60);
insert into item(name, wss_id) values('new clothes', -70);
insert into item(name, wss_id) values('random stuff', -80);
insert into item(name, wss_id) values('heater', -90);
insert into item(name, wss_id) values('batteries', -95);

insert into site_item(site_id, item_id, item_status_id) values(
    (select id from site where name = 'site1'),
    (select id from item where name = 'water'),
    (select id from item_status where name = 'Available')
   );
insert into site_item(site_id, item_id, item_status_id, wss_id) values(
    (select id from site where name = 'site1'),
    (select id from item where name = 'new clothes'),
    (select id from item_status where name = 'Urgently Needed'),
    -10
   );
insert into site_item(site_id, item_id, item_status_id) values(
    (select id from site where name = 'site1'),
    (select id from item where name = 'used clothes'),
    (select id from item_status where name = 'Oversupply')
   );
 insert into site_item(site_id, item_id, item_status_id) values(
    (select id from site where name = 'site2'),
    (select id from item where name = 'used clothes'),
    (select id from item_status where name = 'Oversupply')
   );
insert into site_item(site_id, item_id, item_status_id) values(
    (select id from site where name = 'site2'),
    (select id from item where name = 'new clothes'),
    (select id from item_status where name = 'Oversupply')
  );
 insert into site_item(site_id, item_id, item_status_id) values(
    (select id from site where name = 'site2'),
    (select id from item where name = 'water'),
    (select id from item_status where name = 'Oversupply')
   );

-- insert item for inactive 'site3'
 insert into site_item(site_id, item_id, item_status_id) values(
    (select id from site where name = 'site3'),
    (select id from item where name = 'water'),
    (select id from item_status where name = 'Needed')
   );
