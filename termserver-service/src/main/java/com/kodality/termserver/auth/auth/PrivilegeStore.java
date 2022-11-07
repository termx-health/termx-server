package com.kodality.termserver.auth.auth;

import com.kodality.commons.cache.CacheManager;
import com.kodality.termserver.auth.Privilege;
import com.kodality.termserver.auth.PrivilegeQueryParams;
import com.kodality.termserver.auth.PrivilegeResource;
import com.kodality.termserver.auth.PrivilegeResource.PrivilegeResourceActions;
import com.kodality.termserver.auth.privilege.PrivilegeService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PrivilegeStore {

  private final CacheManager privilegeCache = new CacheManager();
  private final PrivilegeService privilegeService;


  public PrivilegeStore(PrivilegeService privilegeService) {
    privilegeCache.initCache("privilege-cache", 1000, 600);
    this.privilegeService = privilegeService;
  }

  public Set<String> getPrivileges(List<String> roles) {
    if (roles == null) {
      return Set.of();
    }
    return roles.stream().flatMap(r -> getPrivileges(r).stream()).collect(Collectors.toSet());
  }

  public Set<String> getPrivileges(String role) {
    if (StringUtils.isEmpty(role)) {
      return Set.of();
    }
    return this.privilegeCache.get("privilege-cache", role, () -> {
      PrivilegeQueryParams params = new PrivilegeQueryParams();
      params.setCode(role);
      params.setLimit(1);
      return this.privilegeService.query(params).findFirst()
          .map(p -> p.getResources().stream().flatMap(r -> calculate(r).stream()).collect(Collectors.toSet()))
          .orElse(Set.of());
    });
  }

  private List<String> calculate(PrivilegeResource resource) {
    String resourceType = resource.getResourceType();
    if (resourceType.equals("Admin")) {
      return List.of("admin");
    }
    String id = resource.getResourceId();
    String type = resourceType;
    if (resourceType.equals("Any")) {
      type = "*";
    }
    if (id == null) {
      id = "*";
    }
    PrivilegeResourceActions actions = resource.getActions();
    if (actions == null) {
      log.error("Invalid privilege resource with id {}: missing actions", resource.getId());
      return List.of();
    }
    List<String> privileges = new ArrayList<>();
    if (actions.isView()) {
      privileges.add(dottedPrivilege(id, type, "view"));
    }
    if (actions.isEdit()) {
      privileges.add(dottedPrivilege(id, type, "edit"));
    }
    if (actions.isPublish()) {
      privileges.add(dottedPrivilege(id, type, "publish"));
    }
    return privileges;
  }

  private String dottedPrivilege(String id, String type, String action) {
    return String.format("%s.%s.%s", id, type, action);
  }
}
