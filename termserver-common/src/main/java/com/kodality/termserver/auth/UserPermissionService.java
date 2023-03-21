package com.kodality.termserver.auth;

import com.kodality.commons.exception.ForbiddenException;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

import static com.kodality.termserver.auth.SessionInfo.ADMIN;

@Singleton
@RequiredArgsConstructor
public class UserPermissionService {

  public List<String> getPermittedResourceIds(String resourceType, String action) {
    Collection<String> userPrivileges = SessionStore.require().getPrivileges();
    boolean allResourcesAccessible = checkAllResourcesPermitted(resourceType, action, userPrivileges);
    if (allResourcesAccessible) {
      return List.of();
    }
    String suffix = "." + resourceType + "." + action;
    return userPrivileges.stream().filter(p -> p.endsWith(suffix)).map(p -> p.replace(suffix, "")).collect(Collectors.toList());
  }

  public void checkPermitted(String resourceId, String resourceType, String action) {
    Collection<String> userPrivileges = SessionStore.require().getPrivileges();
    boolean allResourcesAccessible = checkAllResourcesPermitted(resourceType, action, userPrivileges);
    if (allResourcesAccessible) {
      return;
    }
    String resource = resourceId + "." + resourceType + "." + action;
    userPrivileges.stream().filter(p -> p.equals(resource)).findFirst()
        .orElseThrow(() -> new ForbiddenException("User is not allowed to " + action + " this resource"));
  }

  private boolean checkAllResourcesPermitted(String resourceType, String action, Collection<String> userPrivileges) {
    return userPrivileges.stream().anyMatch(p -> p.equals(ADMIN) ||
        p.equals("*." + action) ||
        p.equals("*.*." + action) ||
        p.equals("*." + resourceType + "." + action));
  }
}
