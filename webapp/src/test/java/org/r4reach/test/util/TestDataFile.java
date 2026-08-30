package org.r4reach.test.util;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.AllArgsConstructor;

/** Can be used to pull data from files in "test/resources" */
@AllArgsConstructor
public enum TestDataFile {
  // Big data file with SQL statements to seed test database, preps for unit tests.
  TEST_DATA_SCHEMA("/TestData.sql"),

  // Example input from Make for a status change JSON
  STATUS_CHANGE_JSON("/webhook/json/status-change.json"),

  // A Make encoded string that is missing the auth secret
  MISSING_AUTH("/webhook/json/no-auth-string.json"),

  // A Make encoded string that has an incorrect auth value
  INCORRECT_AUTH("/webhook/json/incorrect-auth-string.json"),

  // contains a value with a quote in it. Encoded as  \\\"
  DATA_CONTAINS_DOUBLE_QUOTE("/webhook/json/double-escaped-quote-in-value.json"),

  DELIVERY_TRAINING_DATA("/route-weighting/deliveries.json"),

  DELIVERY_DATA_JSON("/delivery/full-delivery-data.json"),
  ;
  private final String path;

  public String readData() {
    try {
      return Files.readString(Path.of(TestDataFile.class.getResource(path).toURI()));
    } catch (Exception e) {
      throw new RuntimeException("Error with data file: " + path, e);
    }
  }
}
