/**
  Canonicalize every stored phone number to 11 digits in US country-code form (digits only,
  leading 1) -- the form produced by PhoneNumberUtil.toCanonical.

  Phones were stored inconsistently: wss_user.phone held digits-only 10- (occasionally 11-) digit
  values, while the site / driver / additional_site_manager columns kept their original entered
  formatting. A single canonical form lets lookups compare by equality, and is the precondition for
  consolidating these scattered identities onto wss_user (the next migration).

  The mirror columns (site.contact_number, site.og_contact_number, additional_site_manager.phone,
  driver.phone) are normalized here too so that the phone-match lookups keep working until those
  columns are retired. Blank contact numbers (a few sites have them) reduce to '' and are left as-is.
 */

-- One statement per column: strip non-digits, and prefix the country code onto 10-digit numbers.
-- Rows whose digits are empty (blank contact numbers) are skipped so they stay blank.
update wss_user
set phone =
  case
    when length(regexp_replace(phone, '[^0-9]+', '', 'g')) = 10
      then '1' || regexp_replace(phone, '[^0-9]+', '', 'g')
    else regexp_replace(phone, '[^0-9]+', '', 'g')
  end
where regexp_replace(phone, '[^0-9]+', '', 'g') <> '';

update site
set contact_number =
  case
    when length(regexp_replace(contact_number, '[^0-9]+', '', 'g')) = 10
      then '1' || regexp_replace(contact_number, '[^0-9]+', '', 'g')
    else regexp_replace(contact_number, '[^0-9]+', '', 'g')
  end
where regexp_replace(contact_number, '[^0-9]+', '', 'g') <> '';

update site
set og_contact_number =
  case
    when length(regexp_replace(og_contact_number, '[^0-9]+', '', 'g')) = 10
      then '1' || regexp_replace(og_contact_number, '[^0-9]+', '', 'g')
    else regexp_replace(og_contact_number, '[^0-9]+', '', 'g')
  end
where regexp_replace(og_contact_number, '[^0-9]+', '', 'g') <> '';

update additional_site_manager
set phone =
  case
    when length(regexp_replace(phone, '[^0-9]+', '', 'g')) = 10
      then '1' || regexp_replace(phone, '[^0-9]+', '', 'g')
    else regexp_replace(phone, '[^0-9]+', '', 'g')
  end
where regexp_replace(phone, '[^0-9]+', '', 'g') <> '';

update driver
set phone =
  case
    when length(regexp_replace(phone, '[^0-9]+', '', 'g')) = 10
      then '1' || regexp_replace(phone, '[^0-9]+', '', 'g')
    else regexp_replace(phone, '[^0-9]+', '', 'g')
  end
where regexp_replace(phone, '[^0-9]+', '', 'g') <> '';

-- wss_user.phone is now always 11 digits; tighten the old "length > 9" check to match.
alter table wss_user drop constraint wss_user_phone_min_length;
alter table wss_user
  add constraint wss_user_phone_canonical_length check (length(phone) = 11);
