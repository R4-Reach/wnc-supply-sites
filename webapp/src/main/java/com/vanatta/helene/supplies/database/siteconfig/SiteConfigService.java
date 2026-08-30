package com.vanatta.helene.supplies.database.siteconfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads and writes site-wide configuration (API keys, Twilio settings) backed by the {@code
 * site_config} table. Secret values (see {@link SiteConfigKey#isSecret()}) are transparently
 * encrypted on write and decrypted on read via {@link DbSecretCipher}.
 *
 * <p>The table ships empty and there is no fall-back to environment variables: a value read before
 * an admin sets it via the Site Config page is simply absent.
 */
@Service
public class SiteConfigService {

  private final Jdbi jdbi;
  private final DbSecretCipher cipher;

  /** Non-null only for the {@link #withValues} test stub; when set, the DB is not touched. */
  private final Map<SiteConfigKey, String> stubValues;

  @Autowired
  SiteConfigService(Jdbi jdbi, DbSecretCipher cipher) {
    this.jdbi = jdbi;
    this.cipher = cipher;
    this.stubValues = null;
  }

  private SiteConfigService(Map<SiteConfigKey, String> stubValues) {
    this.jdbi = null;
    this.cipher = null;
    this.stubValues = new HashMap<>(stubValues);
  }

  /** Test factory: serves the given plaintext values without a database or cipher. Read-only. */
  public static SiteConfigService withValues(Map<SiteConfigKey, String> values) {
    return new SiteConfigService(values);
  }

  /** The current plaintext value for a key, decrypted if secret; empty when unset or blank. */
  public Optional<String> get(SiteConfigKey key) {
    if (stubValues != null) {
      return Optional.ofNullable(stubValues.get(key)).filter(v -> !v.isBlank());
    }
    return SiteConfigDao.getValue(jdbi, key.name())
        .filter(stored -> !stored.isEmpty())
        .map(stored -> key.isSecret() ? cipher.decrypt(stored) : stored);
  }

  /** The current plaintext value, or the empty string when unset. */
  public String getOrEmpty(SiteConfigKey key) {
    return get(key).orElse("");
  }

  /** Whether a (non-blank) value is stored, without exposing a secret's value. */
  public boolean isSet(SiteConfigKey key) {
    return get(key).isPresent();
  }

  /** Inserts or overwrites a key, encrypting first when the key is secret. */
  public void set(SiteConfigKey key, String value) {
    if (stubValues != null) {
      throw new UnsupportedOperationException("stub SiteConfigService is read-only");
    }
    SiteConfigDao.upsert(jdbi, key.name(), key.isSecret() ? cipher.encrypt(value) : value);
  }
}
