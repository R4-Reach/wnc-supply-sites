package com.vanatta.helene.supplies.database.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.siteconfig.SiteConfigKey;
import com.vanatta.helene.supplies.database.siteconfig.SiteConfigService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoogleMapWidgetTest {

  @Test
  void generateMapRef() {
    var from =
        SiteAddress.builder()
            .address("123 very good?")
            .city("twin & city")
            .state("city, hills!")
            .build();
    var to = SiteAddress.builder().address("address").city("twin & city").state("NC").build();

    GoogleMapWidget googleMapWidget =
        new GoogleMapWidget(
            SiteConfigService.withValues(Map.of(SiteConfigKey.GOOGLE_MAPS_API_KEY, "secretKey")));

    String result = googleMapWidget.generateMapSrcRef(from, to);

    String expected =
        """
    https://www.google.com/maps/embed/v1/directions?key=secretKey&\
    origin=123+very+good%3F,twin+%26+city,city%2C+hills%21&destination=address,twin+%26+city,NC\
    """;

    assertThat(result).isEqualTo(expected);
  }
}
