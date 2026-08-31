package org.r4reach.pii;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.util.PiiCrypto;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * One-off remediation for wss_user rows whose encrypted PII was written under the wrong key during
 * the phase-1 rollout, so {@code phone_enc}/{@code name_enc} no longer decrypt under the current
 * {@code DB_PII_KEY} (they fail with an AEAD tag mismatch). The original backfill only ever filled
 * NULL columns, so it can't repair a row that already holds ciphertext -- hence this separate pass.
 *
 * <p>Runs on startup and is self-limiting: it probes each row by attempting to decrypt it, and only
 * rewrites the ones that fail, sourcing the plaintext from {@code wss_user_backup_v96} (the full
 * pre-encryption snapshot V96 kept, which still holds plaintext phone/name). Rows that already
 * decrypt are left byte-for-byte untouched, so once every row is healed this is a no-op and safe to
 * leave enabled across restarts. Remove it once the repair is proven in production.
 *
 * <p>A row that can't be repaired -- broken ciphertext with no matching backup row, or a backup row
 * with no phone -- is logged and skipped rather than failing startup; it means the row postdates
 * the V96 snapshot and must be handled by hand.
 */
@Slf4j
@Component
public class WssUserPiiReencrypt implements ApplicationRunner {

  private final Jdbi jdbi;

  WssUserPiiReencrypt(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  @Override
  public void run(ApplicationArguments args) {
    jdbi.useTransaction(
        handle -> {
          if (!backupTableExists(handle)) {
            log.info("wss_user_backup_v96 is gone; PII re-encryption already retired, skipping.");
            return;
          }

          List<Row> rows =
              handle
                  .createQuery("select id, phone_enc, name_enc from wss_user")
                  .map(
                      (rs, ctx) ->
                          new Row(
                              rs.getLong("id"),
                              rs.getString("phone_enc"),
                              rs.getString("name_enc")))
                  .list();

          int repaired = 0;
          int unrepairable = 0;
          for (Row row : rows) {
            if (decrypts(row.phoneEnc()) && decrypts(row.nameEnc())) {
              continue;
            }
            if (reencryptFromBackup(handle, row.id())) {
              repaired++;
            } else {
              unrepairable++;
            }
          }
          log.info(
              "PII re-encryption: {} row(s) repaired, {} unrepairable (see WARN logs above).",
              repaired,
              unrepairable);
        });
  }

  /**
   * True if the value is absent or decrypts cleanly under the current key; false on tag mismatch.
   */
  private static boolean decrypts(String stored) {
    if (stored == null) {
      return true;
    }
    try {
      PiiCrypto.decrypt(stored);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static boolean reencryptFromBackup(Handle handle, long id) {
    Backup backup =
        handle
            .createQuery("select phone, name from wss_user_backup_v96 where id = :id")
            .bind("id", id)
            .map((rs, ctx) -> new Backup(rs.getString("phone"), rs.getString("name")))
            .findOne()
            .orElse(null);
    if (backup == null || backup.phone() == null) {
      log.warn("wss_user id={} has undecryptable PII but no usable backup row; skipping.", id);
      return false;
    }
    handle
        .createUpdate(
            """
            update wss_user
            set phone_enc = :phoneEnc, phone_hmac = :phoneHmac, name_enc = :nameEnc
            where id = :id
            """)
        .bind("phoneEnc", PiiCrypto.encrypt(backup.phone()))
        .bind("phoneHmac", PiiCrypto.blindIndex(backup.phone()))
        .bind("nameEnc", PiiCrypto.encrypt(backup.name()))
        .bind("id", id)
        .execute();
    return true;
  }

  private static boolean backupTableExists(Handle handle) {
    return handle
        .createQuery("select to_regclass('public.wss_user_backup_v96') is not null")
        .mapTo(Boolean.class)
        .one();
  }

  private record Row(long id, String phoneEnc, String nameEnc) {}

  private record Backup(String phone, String name) {}
}
