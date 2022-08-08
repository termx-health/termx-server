package com.kodality.termserver.auth.auth;

import com.kodality.commons.exception.ForbiddenException;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class UserPermissionService {

  private final UserPrivilegeStore userPrivilegeStore;

  public List<String> getPermittedResourceIds(String resourceType, String action) {
    SessionInfo sessionInfo = SessionStore.require();
    Collection<String> userPrivileges = userPrivilegeStore.getPrivileges(sessionInfo);
    boolean allResourcesAccessible = checkAllResourcesPermitted(resourceType, action, userPrivileges);
    if (allResourcesAccessible) {
      return List.of();
    }
    String suffix = "." + resourceType + "." + action;
    return userPrivileges.stream().filter(p -> p.endsWith(suffix)).map(p -> p.replace(suffix, "")).collect(Collectors.toList());
  }

  public void checkPermitted(String resourceId, String resourceType, String action) {
    SessionInfo sessionInfo = SessionStore.require();
    Collection<String> userPrivileges = userPrivilegeStore.getPrivileges(sessionInfo);
    boolean allResourcesAccessible = checkAllResourcesPermitted(resourceType, action, userPrivileges);
    if (allResourcesAccessible) {
      return;
    }
    String resource = resourceId + "." + resourceType + "." + action;
    userPrivileges.stream().filter(p -> p.equals(resource)).findFirst()
        .orElseThrow(() -> new ForbiddenException("User is not allowed to " + action + " this resource"));
  }

  private boolean checkAllResourcesPermitted(String resourceType, String action, Collection<String> userPrivileges) {
    return userPrivileges.stream().anyMatch(p -> p.equals(AuthorizationFilter.ADMIN) || p.equals("*." + resourceType + "." + action));
  }
}
