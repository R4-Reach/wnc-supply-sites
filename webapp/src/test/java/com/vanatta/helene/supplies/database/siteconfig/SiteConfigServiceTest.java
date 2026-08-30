package com.vanatta.helene.supplies.database.siteconfig;

import static com.vanatta.helene.supplies.database.TestConfiguration.jdbiTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SiteConfigServiceTest {

  private final SiteConfigService service =
      new SiteConfigService(
          jdbiTest, new DbSecretCipher(Base64.getEncoder().encodeToString(new byte[32])));

  @BeforeEach
  void clearConfig() {
    jdbiTest.withHandle(handle -> handle.createUpdate("delete from site_config").execute());
  }

  @Test
  void secretValueRoundTripsButIsEncryptedAtRest() {
    service.set(SiteConfigKey.GOOGLE_MAPS_API_KEY, "plain-api-key");

    assertThat(service.get(SiteConfigKey.GOOGLE_MAPS_API_KEY)).contains("plain-api-key");
    // The raw stored value must not be the plaintext — it is encrypted.
    assertThat(SiteConfigDao.getValue(jdbiTest, SiteConfigKey.GOOGLE_MAPS_API_KEY.name()))
        .isPresent()
        .get()
        .isNotEqualTo("plain-api-key");
  }

  @Test
  void plaintextValueIsStoredAsIs() {
    service.set(SiteConfigKey.TWILIO_FROM_NUMBER, "+15551112222");

    assertThat(service.get(SiteConfigKey.TWILIO_FROM_NUMBER)).contains("+15551112222");
    assertThat(SiteConfigDao.getValue(jdbiTest, SiteConfigKey.TWILIO_FROM_NUMBER.name()))
        .contains("+15551112222");
  }

  @Test
  void overwriteReplacesValue() {
    service.set(SiteConfigKey.TWILIO_ACCOUNT_SID, "first");
    service.set(SiteConfigKey.TWILIO_ACCOUNT_SID, "second");
    assertThat(service.get(SiteConfigKey.TWILIO_ACCOUNT_SID)).contains("second");
  }

  @Test
  void unsetKeyIsAbsent() {
    assertThat(service.get(SiteConfigKey.TWILIO_AUTH_TOKEN)).isEmpty();
    assertThat(service.isSet(SiteConfigKey.TWILIO_AUTH_TOKEN)).isFalse();
    assertThat(service.getOrEmpty(SiteConfigKey.TWILIO_AUTH_TOKEN)).isEmpty();

    service.set(SiteConfigKey.TWILIO_AUTH_TOKEN, "token");
    assertThat(service.isSet(SiteConfigKey.TWILIO_AUTH_TOKEN)).isTrue();
  }

  @Test
  void stubServiceIsReadOnly() {
    SiteConfigService stub =
        SiteConfigService.withValues(Map.of(SiteConfigKey.GOOGLE_MAPS_API_KEY, "k"));
    assertThat(stub.getOrEmpty(SiteConfigKey.GOOGLE_MAPS_API_KEY)).isEqualTo("k");
    assertThatThrownBy(() -> stub.set(SiteConfigKey.GOOGLE_MAPS_API_KEY, "x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
