package org.r4reach.siteconfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails application startup when the at-rest encryption keys are missing or still the throwaway dev
 * default, so production can never silently encrypt secrets and PII under a publicly-known key.
 *
 * <p>Two independent keys are checked: {@code DB_ENCRYPTION_KEY} ({@code db.encryption.key}, guards
 * site-config secrets such as the Twilio and Maps credentials) and {@code DB_PII_KEY} (guards user
 * names/phones and derives the blind index). Both default to the same all-zero base64 value in
 * {@code application.properties} / {@link org.r4reach.util.PiiCrypto} so local dev and tests run
 * without setup; that default is public and must never reach a real deployment.
 *
 * <p>The guard is active outside the {@code local} and {@code test} profiles, i.e. in staging and
 * production. Those environments must set real base64-encoded 16/24/32-byte keys before deploying —
 * with the default key in place, the app refuses to boot rather than running unprotected.
 */
@Component
@Profile("!local & !test")
public class EncryptionKeyGuard {

  /** The public throwaway key shipped for local dev and tests; forbidden in real deployments. */
  private static final String DEV_DEFAULT_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

  public EncryptionKeyGuard(@Value("${db.encryption.key}") String siteConfigKey) {
    requireRealKey("DB_ENCRYPTION_KEY (db.encryption.key)", siteConfigKey);
    requireRealKey("DB_PII_KEY", System.getenv("DB_PII_KEY"));
  }

  private static void requireRealKey(String name, String key) {
    if (key == null || key.isBlank() || DEV_DEFAULT_KEY.equals(key)) {
      throw new IllegalStateException(
          name
              + " is unset or still the throwaway dev default; refusing to start. Set a real"
              + " base64-encoded encryption key in this environment (only the local/test profiles"
              + " may use the default).");
    }
  }
}
