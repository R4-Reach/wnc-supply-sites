package org.r4reach.pii;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.r4reach.auth.setup.password.SetupPasswordHelper;
import org.r4reach.util.PiiCrypto;

class WssUserPiiBackfillTest {

  // Stored in canonical 11-digit form (see PhoneNumberUtil.toCanonical).
  private static final String PHONE = "15550009876";
  private static final String NAME = "Backfill Tester";

  @BeforeEach
  void cleanDb() {
    SetupPasswordHelper.setup();
  }

  @Test
  void fillsEncryptedColumnsForPreEncryptionRows() {
    insertPlaintextRow(PHONE, NAME);

    new WssUserPiiBackfill(jdbiTest).run(null);

    assertThat(PiiCrypto.decrypt(stored("phone_enc"))).isEqualTo(PHONE);
    assertThat(stored("phone_hmac")).isEqualTo(PiiCrypto.blindIndex(PHONE));
    assertThat(PiiCrypto.decrypt(stored("name_enc"))).isEqualTo(NAME);
  }

  @Test
  void isIdempotent() {
    insertPlaintextRow(PHONE, NAME);
    new WssUserPiiBackfill(jdbiTest).run(null);
    String afterFirst = stored("phone_enc");

    // Already-backfilled rows are skipped, so the ciphertext is left exactly as-is (not
    // re-encrypted
    // to a fresh random IV).
    new WssUserPiiBackfill(jdbiTest).run(null);

    assertThat(stored("phone_enc")).isEqualTo(afterFirst);
  }

  private static void insertPlaintextRow(String phone, String name) {
    jdbiTest.withHandle(
        handle ->
            handle
                .createUpdate("insert into wss_user(phone, name) values (:phone, :name)")
                .bind("phone", phone)
                .bind("name", name)
                .execute());
  }

  private static String stored(String column) {
    Optional<String> value =
        jdbiTest.withHandle(
            handle ->
                handle
                    .createQuery("select " + column + " from wss_user where phone = :phone")
                    .bind("phone", PHONE)
                    .mapTo(String.class)
                    .findOne());
    return value.orElse(null);
  }
}
