package org.r4reach.siteconfig;

import java.util.Optional;
import org.jdbi.v3.core.Jdbi;

/**
 * Raw key/value access over the {@code site_config} table. Values are stored as-is (the encryption
 * of secret values is handled a layer up, in {@link SiteConfigService}).
 */
public class SiteConfigDao {

  /** The stored (possibly encrypted) value for a key, or empty when the key has no row. */
  public static Optional<String> getValue(Jdbi jdbi, String configKey) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("select config_value from site_config where config_key = :key")
                .bind("key", configKey)
                .mapTo(String.class)
                .findOne());
  }

  /** Inserts or overwrites the stored value for a key. */
  public static void upsert(Jdbi jdbi, String configKey, String configValue) {
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into site_config(config_key, config_value) values (:key, :value)
                    on conflict (config_key) do update set config_value = excluded.config_value
                    """)
                .bind("key", configKey)
                .bind("value", configValue)
                .execute());
  }
}
