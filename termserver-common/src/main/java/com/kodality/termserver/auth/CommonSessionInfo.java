package com.kodality.termserver.auth;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.ArrayUtils;

@Getter
@Setter
@Accessors(chain = true)
public class CommonSessionInfo {
  private String username;
  private Set<String> privileges;
  private String lang;

  public boolean hasAnyPrivilege(Collection<String> privileges) {
    return privileges.stream().anyMatch(this::hasPrivilege);
  }

  public boolean hasAnyPrivilege(String... privileges) {
    return hasAnyPrivilege(Arrays.asList(privileges));
  }

  private boolean hasPrivilege(String privilege) {
    return privileges != null && privileges.stream().anyMatch(p -> privilegesMatch(p, privilege));
  }

  private boolean privilegesMatch(String userPrivilege, String searchPrivilege) {
    String[] authParts = searchPrivilege.split("\\.");
    String[] upParts = userPrivilege.split("\\.");

    if (authParts.length == 2 && authParts[0].equals("*")) { // handle special case like '*.view'
      authParts = ArrayUtils.addAll(new String[]{"*"}, authParts);
    }

    if (upParts.length == 2 && upParts[0].equals("*")) { // handle special case like '*.view'
      upParts = ArrayUtils.addAll(new String[]{"*"}, upParts);
    }

    if (upParts.length != 3 && authParts.length != 3) {
      return false;
    }
    return match(upParts[0], authParts[0]) && match(upParts[1], authParts[1]) && match(upParts[2], authParts[2]);
  }

  private static boolean match(String upPart, String apPart) {
    return upPart.equals(apPart) || upPart.equals("*") || apPart.equals("*");
  }
}
