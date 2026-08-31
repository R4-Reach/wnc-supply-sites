package org.r4reach.pii;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.r4reach.auth.setup.password.SetupPasswordHelper;
import org.r4reach.siteconfig.DbSecretCipher;
import org.r4reach.util.PiiCrypto;

class WssUserPiiReencryptTest {

  // Phones are stored in canonical 11-digit form (see PhoneNumberUtil.toCanonical).
  private static final String BROKEN_PHONE = "15550001111";
  private static final String BROKEN_NAME = "Borked User";
  private static final String HEALTHY_PHONE = "15550002222";

  // A cipher keyed differently from the one PiiCrypto uses in tests, so its output fails to decrypt
  // under the current key -- exactly as the mis-keyed phase-1 rows do in prod.
  private static final DbSecretCipher WRONG_KEY_CIPHER = new DbSecretCipher(base64Key((byte) 7));

  @BeforeEach
  void cleanDb() {
    SetupPasswordHelper.setup();
    jdbiTest.withHandle(handle -> handle.createUpdate("delete from wss_user_backup_v96").execute());
  }

  @Test
  void repairsMisKeyedRowFromBackup() {
    long id =
        insertUser(
            WRONG_KEY_CIPHER.encrypt(BROKEN_PHONE),
            "stale-hmac",
            WRONG_KEY_CIPHER.encrypt(BROKEN_NAME));
    insertBackup(id, BROKEN_PHONE, BROKEN_NAME);

    new WssUserPiiReencrypt(jdbiTest).run(null);

    assertThat(PiiCrypto.decrypt(stored(id, "phone_enc"))).isEqualTo(BROKEN_PHONE);
    assertThat(stored(id, "phone_hmac")).isEqualTo(PiiCrypto.blindIndex(BROKEN_PHONE));
    assertThat(PiiCrypto.decrypt(stored(id, "name_enc"))).isEqualTo(BROKEN_NAME);
  }

  @Test
  void leavesHealthyRowUntouched() {
    long id =
        insertUser(PiiCrypto.encrypt(HEALTHY_PHONE), PiiCrypto.blindIndex(HEALTHY_PHONE), null);
    String before = stored(id, "phone_enc");

    // No backup row for this user at all: proving a decryptable row is never even consulted.
    new WssUserPiiReencrypt(jdbiTest).run(null);

    assertThat(stored(id, "phone_enc")).isEqualTo(before);
  }

  @Test
  void isIdempotent() {
    long id = insertUser(WRONG_KEY_CIPHER.encrypt(BROKEN_PHONE), "stale-hmac", null);
    insertBackup(id, BROKEN_PHONE, null);
    new WssUserPiiReencrypt(jdbiTest).run(null);
    String afterFirst = stored(id, "phone_enc");

    // Second pass sees a row that now decrypts, so it re-encrypts nothing (no fresh IV).
    new WssUserPiiReencrypt(jdbiTest).run(null);

    assertThat(stored(id, "phone_enc")).isEqualTo(afterFirst);
  }

  @Test
  void skipsUnrepairableRowWithoutFailing() {
    long id = insertUser(WRONG_KEY_CIPHER.encrypt(BROKEN_PHONE), "stale-hmac", null);
    String before = stored(id, "phone_enc");

    // No backup row: the runner logs and moves on rather than throwing during startup.
    new WssUserPiiReencrypt(jdbiTest).run(null);

    assertThat(stored(id, "phone_enc")).isEqualTo(before);
    assertThatThrownBy(() -> PiiCrypto.decrypt(stored(id, "phone_enc")))
        .isInstanceOf(RuntimeException.class);
  }

  private static long insertUser(String phoneEnc, String phoneHmac, String nameEnc) {
    return jdbiTest.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    insert into wss_user(phone_enc, phone_hmac, name_enc)
                    values (:phoneEnc, :phoneHmac, :nameEnc)
                    returning id
                    """)
                .bind("phoneEnc", phoneEnc)
                .bind("phoneHmac", phoneHmac)
                .bind("nameEnc", nameEnc)
                .mapTo(Long.class)
                .one());
  }

  private static void insertBackup(long id, String phone, String name) {
    jdbiTest.withHandle(
        handle ->
            handle
                .createUpdate(
                    "insert into wss_user_backup_v96(id, phone, name) values (:id, :phone, :name)")
                .bind("id", id)
                .bind("phone", phone)
                .bind("name", name)
                .execute());
  }

  private static String stored(long id, String column) {
    return jdbiTest
        .withHandle(
            handle ->
                handle
                    .createQuery("select " + column + " from wss_user where id = :id")
                    .bind("id", id)
                    .mapTo(String.class)
                    .findOne())
        .orElse(null);
  }

  private static String base64Key(byte fill) {
    byte[] key = new byte[32];
    Arrays.fill(key, fill);
    return Base64.getEncoder().encodeToString(key);
  }
}
