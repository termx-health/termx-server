package com.kodality.termserver.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.ArrayUtils;

@Getter
@Setter
@Accessors(chain = true)
public class SessionInfo {
  private String token;
  private String username;
  private Set<String> privileges;
  private String lang;

  private String provider;
  private Map<String, String> providerProperties;

  public interface AuthenticationProvider {
    String sso = "sso";
    String smart = "smart-on-fhir";
  }

  public static final String ADMIN = "admin";

  public boolean hasAnyPrivilege(List<String> authPrivileges) {
    return hasAnyPrivilege(authPrivileges, Optional.empty());
  }

  public boolean hasAnyPrivilege(List<String> authPrivileges, Optional<String> resourceId) {
    return privileges.contains(ADMIN) || authPrivileges.stream().anyMatch(ap -> privileges.stream().anyMatch(up -> privilegesMatch(ap, up, resourceId)));
  }

  private boolean privilegesMatch(String authPrivilege, String userPrivilege, Optional<String> resourceId) {
    if (authPrivilege.equals(ADMIN)) {
      return userPrivilege.equals(ADMIN);
    }
    String[] authParts = authPrivilege.split("\\.");
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
    return match(upParts[0], authParts[0]) && match(upParts[1], authParts[1]) && match(upParts[2], authParts[2]) && match(upParts[0], resourceId.orElse("*"));
  }

  private boolean match(String upPart, String apPart) {
    return upPart.equals(apPart) || upPart.equals("*") || apPart.equals("*");
  }

}
