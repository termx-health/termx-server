package com.kodality.termx.auth;

import com.kodality.termx.core.auth.SessionInfo;
import com.kodality.termx.uam.privilege.PrivilegeStore;
import io.micronaut.http.HttpRequest;
import java.util.Set;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class GuestSessionProvider extends SessionProvider {
  public static final String GUEST = "guest";
  private final PrivilegeStore privilegeStore;

  @Override
  public int getOrder() {
    return 30;
  }

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    Set<String> privileges = privilegeStore.getPrivileges(GUEST);
    SessionInfo info = new SessionInfo();
    info.setPrivileges(privileges);
    info.setUsername(GUEST);
    return info;
  }
}
