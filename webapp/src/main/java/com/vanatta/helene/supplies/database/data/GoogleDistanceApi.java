package com.vanatta.helene.supplies.database.data;

import com.vanatta.helene.supplies.database.util.HttpGetSender;
import java.util.Map;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleDistanceApi {
  private final String apiKey;

  private static final String googleMapsApiUrl =
      "https://maps.googleapis.com/maps/api/distancematrix/json";

  /**
   * Outcome of a distance lookup. {@link #OK} → write the distance. {@link #INVALID_PAIR} → mark
   * the pair invalid permanently (Google understood the request but can't route it). {@link
   * #TRANSIENT_FAILURE} → leave the pair NULL for retry; Google itself is unhealthy (bad key,
   * billing, throttle) and the next pair almost certainly fails the same way.
   */
  public enum ResponseStatus {
    OK,
    INVALID_PAIR,
    TRANSIENT_FAILURE
  }

  // @VisibleForTesting
  public static GoogleDistanceApi stubbed() {
    return new GoogleDistanceApi("") {
      @Override
      public GoogleDistanceResponse queryDistance(SiteAddress from, SiteAddress to) {
        return GoogleDistanceResponse.builder()
            .distance(20.0)
            .duration(320L)
            .status(ResponseStatus.OK)
            .build();
      }
    };
  }

  public GoogleDistanceApi(@Value("${google.maps.api.key}") String apiKey) {
    this.apiKey = apiKey;
  }

  public GoogleDistanceResponse queryDistance(SiteAddress from, SiteAddress to) {
    Map<String, String> params =
        Map.of(
            "key",
            apiKey,
            "origins",
            from.toEncodedUrlValue(),
            "destinations",
            to.toEncodedUrlValue());

    GoogleDistanceJson json =
        HttpGetSender.sendRequest(googleMapsApiUrl, params, GoogleDistanceJson.class);
    ResponseStatus status = json.classify();
    GoogleDistanceResponse.GoogleDistanceResponseBuilder builder =
        GoogleDistanceResponse.builder().status(status);
    if (status == ResponseStatus.OK) {
      builder.distance(json.getDistance()).duration(json.getDuration());
    }
    return builder.build();
  }

  @Builder
  @lombok.Value
  public static class GoogleDistanceResponse {
    Long duration;
    Double distance;
    ResponseStatus status;
  }

  public static class GoogleDistanceJson {
    DistanceMatrixRow[] rows;

    static class DistanceMatrixRow {
      DistanceMatrixElement[] elements;

      static class DistanceMatrixElement {
        Distance distance;
        Duration duration;
        String status;

        static class Distance {
          long value;
        }

        static class Duration {
          long value;
        }
      }
    }

    ResponseStatus classify() {
      if (rows == null
          || rows.length == 0
          || rows[0].elements == null
          || rows[0].elements.length == 0) {
        // Google itself failed the request (REQUEST_DENIED, OVER_QUERY_LIMIT, malformed body).
        // Don't poison the cache — let the scheduler retry next tick.
        return ResponseStatus.TRANSIENT_FAILURE;
      }
      return "OK".equalsIgnoreCase(rows[0].elements[0].status)
          ? ResponseStatus.OK
          : ResponseStatus.INVALID_PAIR;
    }

    Double getDistance() {
      if (rows.length == 0
          || rows[0].elements.length == 0
          || rows[0].elements[0].distance == null) {
        return null;
      }
      long meters = rows[0].elements[0].distance.value;
      double miles = meters / 1609.34;
      // round to the nearest tenth
      return Math.round(miles * 10) / 10.0;
    }

    // returns time duration in seconds
    Long getDuration() {
      if (rows.length == 0
          || rows[0].elements.length == 0
          || rows[0].elements[0].duration == null) {
        return null;
      }
      return rows[0].elements[0].duration.value;
    }
  }
}
