package org.r4reach.jobs.distance;

import org.r4reach.TestConfiguration;

class DistanceTestHelper {

  static void setup() {
    TestConfiguration.setupDatabase();
    String testDataSql =
        """
            insert into site_distance_matrix(
              site1_id, site2_id, distance_miles, drive_time_seconds, valid
            ) values (
              (select id from site where name = 'site1'),
              (select id from site where name = 'site2'),
              30,
              48,
              true
            );

            insert into site_distance_matrix(
              site1_id, site2_id
            ) values (
              (select id from site where name = 'site1'),
              (select id from site where name = 'site4')
            );

            insert into site_distance_matrix(
              site1_id, site2_id, valid
            ) values (
              (select id from site where name = 'site2'),
              (select id from site where name = 'site4'),
              false
            );
            """;
    TestConfiguration.jdbiTest.withHandle(handle -> handle.createScript(testDataSql).execute());
  }
}
