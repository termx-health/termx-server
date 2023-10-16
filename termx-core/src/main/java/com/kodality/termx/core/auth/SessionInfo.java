package com.kodality.termx.core.auth;

import com.kodality.commons.exception.ForbiddenException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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


  public void checkPermitted(String resourceId, String resourceType, String action) {
    checkPermitted(resourceId, resourceType + "." + action);
  }

  public void checkPermitted(String resourceId, String permission) {
    resourceId = resourceId == null ? "*" : resourceId;
    if (!hasPrivilege(resourceId + "." + permission)) {
      throw new ForbiddenException("forbidden");
    }
  }

  public boolean hasPrivilege(String authPrivilege) {
    return hasAnyPrivilege(List.of(authPrivilege));
  }

  public boolean hasAnyPrivilege(List<String> authPrivileges) {
    return privileges != null && authPrivileges.stream().anyMatch(ap -> privileges.stream().anyMatch(up -> privilegesMatch(ap, up)));
  }

  private boolean privilegesMatch(String p1, String p2) {
    String[] p1Parts = split(p1);
    String[] p2Parts = split(p2);
    if (p1Parts.length != 3 && p2Parts.length != 3) {
      return false;
    }
    return match(p1Parts[0], p2Parts[0]) && match(p1Parts[1], p2Parts[1]) && match(p1Parts[2], p2Parts[2]);
  }

  private String[] split(String p) {
    String[] parts = p.split("\\.");
    return new String[]{String.join(".", ArrayUtils.subarray(parts, 0, parts.length - 2)), parts[parts.length - 2], parts[parts.length - 1]};
  }

  private boolean match(String p1, String p2) {
    return p1.equals(p2) || p1.equals("*") || p2.equals("*");
  }

  public <T> List<T> getPermittedResourceIds(String privilege, Function<String, T> mapper) {
    List<String> rids = getPermittedResourceIds(privilege);
    return rids == null ? null : rids.stream().map(mapper).toList();
  }

  public List<String> getPermittedResourceIds(String privilege) {
    String[] p = privilege.split("\\.");
    return getPermittedResourceIds(p[0], p[1]);
  }

  public List<String> getPermittedResourceIds(String resourceType, String action) {
    boolean allResourcesAccessible = getPrivileges().stream().anyMatch(p -> List.of("*.*.*", "*.*." + action, "*." + resourceType + "." + action).contains(p));
    if (allResourcesAccessible) {
      return null;
    }
    String suffix = "." + resourceType + "." + action;
    return getPrivileges().stream().filter(p -> p.endsWith(suffix)).map(p -> p.replace(suffix, "")).toList();
  }

}
