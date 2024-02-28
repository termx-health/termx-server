package com.kodality.termx.user;

import com.kodality.commons.cache.CacheManager;
import com.kodality.termx.auth.Privilege;
import com.kodality.termx.core.user.User;
import com.kodality.termx.core.user.UserProvider;
import com.kodality.termx.uam.privilege.PrivilegeDataHandler;
import com.kodality.termx.uam.privilege.PrivilegeStore;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
public class TermxUserProvider extends UserProvider implements PrivilegeDataHandler {
  private final Optional<OAuthUserHttpClient> authUserHttpClient;
  private final CacheManager cache = new CacheManager();
  private final PrivilegeStore privilegeStore;

  public TermxUserProvider(Optional<OAuthUserHttpClient> authUserHttpClient, PrivilegeStore privilegeStore) {
    this.authUserHttpClient = authUserHttpClient;
    this.privilegeStore = privilegeStore;
    cache.initCache("users", 1, TimeUnit.MINUTES.toSeconds(10));
  }

  @Override
  public List<User> getUsers() {
    return cache.get("users", "query", () -> authUserHttpClient.map(this::getUsers).orElseGet(List::of));
  }

  private List<User> getUsers(OAuthUserHttpClient client) {
    return client.getUsers()
        .join()
        .stream()
        .map(u -> {
          var userRoles = client.getUserRoles(u.getId());
          return new User().setSub(u.getUsername()).setName(getName(u)).setPrivileges(privilegeStore.getPrivileges(userRoles));
        })
        .toList();
  }

  private String getName(OAuthUser user) {
    if (user.getFirstName() != null && user.getLastName() != null) {
      return String.join(",", user.getLastName(), user.getFirstName());
    }
    return user.getUsername();
  }


  @Override
  public void afterPrivilegeSave(Privilege privilege) {
    cache.clearCache("users");
  }

  @Override
  public void afterPrivilegeDelete(Privilege privilege) {
    cache.clearCache("users");
  }
}
