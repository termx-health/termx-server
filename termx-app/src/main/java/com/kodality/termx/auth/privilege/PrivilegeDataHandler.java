package com.kodality.termx.auth.privilege;

import com.kodality.termx.auth.Privilege;

public interface PrivilegeDataHandler {
  default void afterPrivilegeSave(Privilege privilege) {}

  default void afterPrivilegeDelete(Privilege privilege) {}
}
