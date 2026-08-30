package org.r4reach.supplies.filters;

import java.util.List;
import org.jdbi.v3.core.Jdbi;

public class FilterDataDao {
  public static List<String> getAllItems(Jdbi jdbi) {
    String query =
        """
          select name from item order by lower(name)
        """;
    return jdbi.withHandle(handle -> handle.createQuery(query).mapTo(String.class).list());
  }

  public static List<String> getAllActiveSites(
      Jdbi jdbi, AuthenticatedMode authenticatedMode, List<String> stateList) {
    String authenticatedFilter =
        authenticatedMode == AuthenticatedMode.AUTHENTICATED ? "" : "and s.publicly_visible = true";
    String stateFilter = stateList.isEmpty() ? "" : "and c.state in (<stateList>)";

    String query =
        String.format(
            """
        select s.name
        from site s
        join county c on c.id = s.county_id
        where s.active = true
          %s
          %s
        order by lower(s.name)
        """,
            authenticatedFilter, stateFilter);

    return jdbi.withHandle(
        handle -> {
          var jdbiQuery = handle.createQuery(query);
          if (!stateList.isEmpty()) {
            jdbiQuery = jdbiQuery.bindList("stateList", stateList);
          }
          return jdbiQuery.mapTo(String.class).list();
        });
  }
}
