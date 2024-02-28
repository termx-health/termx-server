package com.kodality.termx.uam.privilege;

import com.kodality.termx.auth.Privilege;

public interface PrivilegeDataHandler {
  default void afterPrivilegeSave(Privilege privilege) {}

  default void afterPrivilegeDelete(Privilege privilege) {}
}
