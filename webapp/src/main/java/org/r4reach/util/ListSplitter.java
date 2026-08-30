package org.r4reach.util;

import java.util.List;

public class ListSplitter {

  /**
   * When splitting list three ways - How many items we can have in one column before we split the
   * item list into two.
   */
  // @VisibleForTesting
  static int ITEM_LIST_ONE_COLUMN_MAX = 5;

  // @VisibleForTesting
  static int ITEM_LIST_TWO_COLUMN_MAX = 11;

  /**
   * Splits an incoming list into up to three lists. Uses default threshold for if we split the
   * incoming list into two, and if long enough, we split into three lists.
   */
  public static <T> List<List<T>> splitItemList(List<T> items) {
    return splitItemList(items, ITEM_LIST_ONE_COLUMN_MAX, ITEM_LIST_TWO_COLUMN_MAX);
  }

  /**
   * Splits an incoming list into two, with input parameter for max number of elements in first list
   * before we split it into two.
   */
  public static <T> List<List<T>> splitItemList(List<T> items, int cutOffForTwoLists) {
    return splitItemList(items, cutOffForTwoLists, Integer.MAX_VALUE);
  }

  private static <T> List<List<T>> splitItemList(
      List<T> items, int cutOffForTwoLists, int cutOffForThreeLists) {
    assert items != null;
    assert cutOffForTwoLists >= 0 && cutOffForThreeLists >= 0;
    assert cutOffForTwoLists < cutOffForThreeLists;

    if (items.size() <= cutOffForTwoLists) {
      return List.of(items);
    } else if (items.size() <= cutOffForThreeLists) {
      int splitLocation = items.size() % 2 == 0 ? items.size() / 2 : (items.size() / 2) + 1;
      List<T> one = items.subList(0, splitLocation);
      List<T> two = items.subList(splitLocation, items.size());
      return List.of(one, two);
    } else {

      int firstListSize = items.size() % 3 == 0 ? items.size() / 3 : (items.size() / 3) + 1;
      int secondListSize = items.size() % 3 == 2 ? (items.size() / 3) + 1 : items.size() / 3;
      int thirdListSize = items.size() / 3;
      assert (firstListSize + secondListSize + thirdListSize) == items.size();

      List<T> one = items.subList(0, firstListSize);
      List<T> two = items.subList(firstListSize, firstListSize + secondListSize);
      List<T> three = items.subList(firstListSize + secondListSize, items.size());
      return List.of(one, two, three);
    }
  }
}
