package com.vanatta.helene.supplies.database.siteconfig;

/**
 * The configuration values stored in the {@code site_config} table. The enum constant's name is the
 * {@code config_key} column value, so renaming a constant is a data migration.
 *
 * <p>{@link #secret} values are API credentials: they are encrypted at rest (see {@link
 * DbSecretCipher}) and are never sent back to the browser — the Site Config UI only shows whether
 * they are set and lets an admin overwrite them.
 */
public enum SiteConfigKey {
  GOOGLE_MAPS_API_KEY(true),
  TWILIO_ACCOUNT_SID(false),
  TWILIO_AUTH_TOKEN(true),
  TWILIO_FROM_NUMBER(false),
  ;

  private final boolean secret;

  SiteConfigKey(boolean secret) {
    this.secret = secret;
  }

  public boolean isSecret() {
    return secret;
  }
}
