package org.r4reach.data;

import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.Getter;
import org.r4reach.util.UrlEncode;

@Builder
@Getter
public class SiteAddress {
  @Nonnull private final String address;
  @Nonnull private final String city;
  @Nonnull private final String state;

  public String toEncodedUrlValue() {
    return String.format(
        "%s,%s,%s", UrlEncode.encode(address), UrlEncode.encode(city), UrlEncode.encode(state));
  }
}
