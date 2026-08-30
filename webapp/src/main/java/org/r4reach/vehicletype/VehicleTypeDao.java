package org.r4reach.vehicletype;

import java.util.List;
import org.jdbi.v3.core.Jdbi;

/** Reads and edits the configurable {@code vehicle_type} pick list. */
public class VehicleTypeDao {

  public static List<VehicleType> fetchAll(Jdbi jdbi) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("select id, name from vehicle_type order by id")
                .mapToBean(VehicleType.class)
                .list());
  }

  /**
   * Adds a new type. Blank names are rejected; a name that already exists (case-insensitively) is
   * left untouched. Returns true only when a new row was inserted.
   */
  public static boolean add(Jdbi jdbi, String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    return jdbi.withHandle(
        handle ->
            handle
                    .createUpdate(
                        """
                        insert into vehicle_type(name) values (:name)
                        on conflict do nothing
                        """)
                    .bind("name", name.trim())
                    .execute()
                > 0);
  }

  /** How many drivers currently reference this type; a type in use may not be removed. */
  public static int countDriversUsing(Jdbi jdbi, long id) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("select count(*) from driver where vehicle_type_id = :id")
                .bind("id", id)
                .mapTo(Integer.class)
                .one());
  }

  /**
   * Removes a type, unless a driver still references it. Returns false (removing nothing) when the
   * type is still in use, so the UI can report it.
   */
  public static boolean remove(Jdbi jdbi, long id) {
    if (countDriversUsing(jdbi, id) > 0) {
      return false;
    }
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate("delete from vehicle_type where id = :id")
                .bind("id", id)
                .execute());
    return true;
  }
}
