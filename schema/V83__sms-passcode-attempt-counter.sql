/**
  Tracks failed confirmation attempts against a given SMS passcode so the
  confirm-access-code flow can lock a code out after too many wrong guesses.
 */
alter table sms_passcode add column failed_access_attempts integer not null default 0;
