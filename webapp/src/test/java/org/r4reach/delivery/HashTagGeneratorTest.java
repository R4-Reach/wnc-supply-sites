package org.r4reach.delivery;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;

class HashTagGeneratorTest {

  /** Make sure we can generate rando hashtags without any errors. */
  @RepeatedTest(100)
  void generate() {
    Assertions.assertThat(HashTagGenerator.generate()).isNotEmpty();
  }
}
