package org.r4reach.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ListSplitterTest {

  static List<String> generateListOfLength(int length) {
    List<String> list = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      list.add(String.valueOf(i));
    }
    assertThat(list.size()).isEqualTo(length);
    return list;
  }

  @Nested
  class splitListInTwo {
    @Test
    void split() {
      List<String> tenItems = generateListOfLength(10);
      for (int i = 1; i < 10; i++) {
        List<List<String>> list = ListSplitter.splitItemList(tenItems, i);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).size()).isGreaterThanOrEqualTo(list.get(1).size());
        assertThat(
                list.get(0).size() == list.get(1).size()
                    || list.get(0).size() == list.get(1).size() + 1)
            .isTrue();
      }
      for (int i = 10; i < 12; i++) {
        List<List<String>> list = ListSplitter.splitItemList(tenItems, i);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.getFirst()).hasSize(10);
      }
    }
  }

  @Nested
  class ItemListSplittingInThree {

    @Test
    void noSplitting() {
      for (int i = 0; i <= ListSplitter.ITEM_LIST_ONE_COLUMN_MAX; i++) {
        var input = generateListOfLength(i);

        List<List<String>> result = ListSplitter.splitItemList(input);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).hasSize(i);
      }
    }

    /**
     * When splitting into two columns, the first list should either be equal to the second list, or
     * have one extra element if the total number of element is odd. For example (Using small item
     * number counts):
     *
     * <pre>
     *   [a] -> [a] [-]
     *   [a, b] -> [a] [b]
     *   [a, b, c] -> [a,b] [c]
     * </pre>
     */
    @Test
    void twoColumnSplitting() {
      for (int i = ListSplitter.ITEM_LIST_ONE_COLUMN_MAX + 1;
          i <= ListSplitter.ITEM_LIST_TWO_COLUMN_MAX;
          i++) {
        var input = generateListOfLength(i);

        List<List<String>> result = ListSplitter.splitItemList(input);

        assertThat(result).hasSize(2);

        if (i % 2 == 0) {
          assertThat(result.getFirst()).hasSize((i / 2));
        } else {
          assertThat(result.getFirst())
              .describedAs(
                  String.format(
                      "Total size: %s, first list: %s, second list: %s, expected size of second list: %s",
                      input.size(), result.getFirst(), result.get(1), i / 2))
              .hasSize((i / 2) + 1);
        }
        assertThat(result.get(1))
            .describedAs(
                String.format(
                    "Total size: %s, first list: %s, second list: %s, expected size of second list: %s",
                    input.size(), result.getFirst(), result.get(1), i / 2))
            .hasSize(i / 2);
      }
    }

    @Test
    void threeColumnSplitting() {
      for (int i = ListSplitter.ITEM_LIST_TWO_COLUMN_MAX + 1;
          i <= ListSplitter.ITEM_LIST_TWO_COLUMN_MAX + 100;
          i++) {
        var input = generateListOfLength(i);

        List<List<String>> result = ListSplitter.splitItemList(input);

        assertThat(result).hasSize(3);
        // first column should have an extra element unless the total list size is divisble by 3
        int size1Expected = (i % 3 == 0) ? i / 3 : i / 3 + 1;
        // second column will have an extra element only when the list size mod 3 is equal to 2
        int size2Expected = (i % 3 == 2) ? i / 3 + 1 : i / 3;
        // third column expected size is always the floor of the total list size divided by 3
        int size3Expected = i / 3;

        String describe =
            String.format(
                "Total size: %s, first list: %s, second list: %s, third list: %s, expected sizes: %s, %s, %s",
                input.size(),
                result.getFirst(),
                result.get(1),
                result.get(2),
                size1Expected,
                size2Expected,
                size3Expected);
        assertThat(result.getFirst()).describedAs(describe).hasSize(size1Expected);
        assertThat(result.get(1)).describedAs(describe).hasSize(size2Expected);
        assertThat(result.get(2)).describedAs(describe).hasSize(size3Expected);
      }
    }
  }
}
