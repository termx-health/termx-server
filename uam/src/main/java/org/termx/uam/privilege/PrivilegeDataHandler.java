package org.termx.uam.privilege;

import org.termx.auth.Privilege;

public interface PrivilegeDataHandler {
  default void afterPrivilegeSave(Privilege privilege) {}

  default void afterPrivilegeDelete(Privilege privilege) {}
}
