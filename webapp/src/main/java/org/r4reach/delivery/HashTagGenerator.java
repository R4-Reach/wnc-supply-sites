package org.r4reach.delivery;

import java.util.List;
import java.util.Random;

public class HashTagGenerator {

  private static final Random rand = new Random();
  private static final List<String> hashTags =
      List.of(
          "recoverStrong",
          "recovery",
          "community",
          "strongRecovery",
          "helpingEachOther",
          "inThisTogether",
          "weAreStrong",
          "supportingNeighbors",
          "helpingNeighbors",
          "youAreAwesome",
          "making-a-difference");

  static String generate() {
    return hashTags.get(rand.nextInt(hashTags.size()));
  }
}
