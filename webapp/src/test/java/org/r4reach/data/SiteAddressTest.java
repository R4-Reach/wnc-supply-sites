package org.r4reach.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SiteAddressTest {
  @Test
  void toEncodedUrlValue() {

    var address =
        SiteAddress.builder().address("twin & city").city(" cool town ").state("NC").build();

    String result = address.toEncodedUrlValue();

    assertThat(result).isEqualTo("twin+%26+city,cool+town,NC");
  }
}
