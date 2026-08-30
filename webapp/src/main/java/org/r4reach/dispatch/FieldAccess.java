package org.r4reach.dispatch;

/** A single field's access level for a user: editable, visible-but-locked, or not shown at all. */
public enum FieldAccess {
  READ_WRITE,
  READ_ONLY,
  HIDDEN;

  public boolean isWritable() {
    return this == READ_WRITE;
  }

  public boolean isVisible() {
    return this != HIDDEN;
  }
}
