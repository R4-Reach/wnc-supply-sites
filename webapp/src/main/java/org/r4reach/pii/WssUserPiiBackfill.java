package org.r4reach.pii;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.util.PiiCrypto;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Populates the encrypted wss_user columns (phone_enc, phone_hmac, name_enc) for rows that predate
 * the encryption rollout -- phase 1 of the staged cutover added the columns and made every write
 * dual-write, but existing rows still need filling in. Runs on startup; idempotent, so it is a
 * no-op once every row is backfilled and safe to leave enabled across restarts.
 *
 * <p>Stored phones are already canonical (see the V92 migration), so the blind index is computed
 * over the stored value directly. Remove this once the follow-up migration has dropped the
 * plaintext columns.
 */
@Slf4j
@Component
public class WssUserPiiBackfill implements ApplicationRunner {

  private final Jdbi jdbi;

  WssUserPiiBackfill(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  @Override
  public void run(ApplicationArguments args) {
    jdbi.useTransaction(
        handle -> {
          List<Row> pending =
              handle
                  .createQuery(
                      """
                      select id, phone, name from wss_user
                      where phone_enc is null or (name is not null and name_enc is null)
                      """)
                  .map(
                      (rs, ctx) ->
                          new Row(rs.getLong("id"), rs.getString("phone"), rs.getString("name")))
                  .list();
          if (pending.isEmpty()) {
            return;
          }
          for (Row row : pending) {
            handle
                .createUpdate(
                    """
                    update wss_user
                    set phone_enc = :phoneEnc, phone_hmac = :phoneHmac, name_enc = :nameEnc
                    where id = :id
                    """)
                .bind("phoneEnc", PiiCrypto.encrypt(row.phone()))
                .bind("phoneHmac", PiiCrypto.blindIndex(row.phone()))
                .bind("nameEnc", PiiCrypto.encrypt(row.name()))
                .bind("id", row.id())
                .execute();
          }
          log.info("Backfilled encrypted PII for {} wss_user row(s).", pending.size());
        });
  }

  private record Row(long id, String phone, String name) {}
}
