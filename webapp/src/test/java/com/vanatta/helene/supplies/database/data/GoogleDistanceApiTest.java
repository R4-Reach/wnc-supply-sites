package com.vanatta.helene.supplies.database.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class GoogleDistanceApiTest {

  // note response value for distance is in meters & response value for time is in seconds
  private static final String sampleResponse =
      """
{
   "destination_addresses" :\s
   [
      "Flat Rock, NC 28731, USA"
   ],
   "origin_addresses" :\s
   [
      "Mills River, NC 28759, USA"
   ],
   "rows" :\s
   [
      {
         "elements" :\s
         [
            {
               "distance" :\s
               {
                  "text" : "18.8 mi",
                  "value" : 30217
               },
               "duration" :\s
               {
                  "text" : "28 mins",
                  "value" : 1656
               },
               "status" : "OK"
            }
         ]
      }
   ]
 }
  """;

  private static final String invalidAddressResponse =
      """
      {
         "destination_addresses" :\s
         [
            ""
         ],
         "origin_addresses" :\s
         [
            "Mills River, NC 28759, USA"
         ],
         "rows" :\s
         [
            {
               "elements" :\s
               [
                  {
                     "status" : "NOT_FOUND"
                  }
               ]
            }
         ],
         "status" : "OK"
      }
      """;

  @Test
  void responseParsing() {
    var result = new Gson().fromJson(sampleResponse, GoogleDistanceApi.GoogleDistanceJson.class);
    assertThat(result.classify()).isEqualTo(GoogleDistanceApi.ResponseStatus.OK);
    assertThat(result.getDistance()).isEqualTo(18.8);
    assertThat(result.getDuration()).isEqualTo(1656L);
  }

  /** NOT_FOUND on a per-element status means the address didn't geocode — pair-permanent. */
  @Test
  void invalidAddressResponseParsing() {
    var result =
        new Gson().fromJson(invalidAddressResponse, GoogleDistanceApi.GoogleDistanceJson.class);
    assertThat(result.classify()).isEqualTo(GoogleDistanceApi.ResponseStatus.INVALID_PAIR);
    assertThat(result.getDistance()).isNull();
    assertThat(result.getDuration()).isNull();
  }

  /**
   * Google returns this shape for REQUEST_DENIED / OVER_QUERY_LIMIT (bad key, billing lapsed,
   * etc.). Used to throw ArrayIndexOutOfBoundsException out of the old isValid() and crash the
   * scheduler — see .docs/google-distance-api-followup.md. Must be classified as TRANSIENT so the
   * scheduler doesn't poison the cache.
   */
  @Test
  void emptyRowsResponseParsing() {
    String body =
        """
        {"destination_addresses":[],"origin_addresses":[],"rows":[],"status":"REQUEST_DENIED"}
        """;
    var result = new Gson().fromJson(body, GoogleDistanceApi.GoogleDistanceJson.class);
    assertThat(result.classify()).isEqualTo(GoogleDistanceApi.ResponseStatus.TRANSIENT_FAILURE);
  }
}
