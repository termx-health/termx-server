package com.kodality.termx.auth.auth;

import com.kodality.commons.cache.CacheManager;
import com.kodality.termx.auth.Privilege;
import com.kodality.termx.auth.PrivilegeQueryParams;
import com.kodality.termx.auth.PrivilegeResource;
import com.kodality.termx.auth.PrivilegeResource.PrivilegeResourceActions;
import com.kodality.termx.auth.privilege.PrivilegeDataHandler;
import com.kodality.termx.auth.privilege.PrivilegeService;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Provider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PrivilegeStore implements PrivilegeDataHandler {
  private final CacheManager privilegeCache = new CacheManager();
  private final Provider<PrivilegeService> privilegeService;

  public PrivilegeStore(Provider<PrivilegeService> privilegeService) {
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
      return this.privilegeService.get().query(params).findFirst()
          .map(p -> p.getResources().stream().flatMap(r -> calculate(r).stream()).collect(Collectors.toSet()))
          .orElse(Set.of());
    });
  }

  private List<String> calculate(PrivilegeResource resource) {
    String resourceType = resource.getResourceType();
    if (resourceType.equals("Admin")) {
      return List.of("*.*.*");
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

  @Override
  public void afterPrivilegeSave(Privilege privilege) {
    privilegeCache.remove("privilege-cache", privilege.getCode());
  }

  @Override
  public void afterPrivilegeDelete(Privilege privilege) {
    privilegeCache.remove("privilege-cache", privilege.getCode());
  }
}
