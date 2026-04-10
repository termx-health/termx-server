package org.termx.auth;

import org.termx.core.auth.SessionInfo;
import org.termx.uam.privilege.PrivilegeStore;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import java.util.Set;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@Requires(property = "auth.guest.disabled", notEquals = StringUtils.TRUE)
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
