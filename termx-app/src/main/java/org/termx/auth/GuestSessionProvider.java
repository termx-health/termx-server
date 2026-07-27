package org.termx.auth;

import org.termx.core.auth.SessionInfo;
import org.termx.uam.privilege.PrivilegeStore;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
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
  private final PrivilegeStore privilegeStore;

  /**
   * Identity of the anonymous session: both the username shown and the privilege role looked up.
   * Defaults to {@code guest} (the role seeded by the default UAM changelog). A deployment whose
   * seed names its anonymous role differently (e.g. {@code KKL-GUEST}) sets this so the guest
   * gets that role's privileges. Package-private for Micronaut field injection.
   */
  @Value("${auth.guest.name:guest}")
  String guestName;

  @Override
  public int getOrder() {
    return 30;
  }

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    Set<String> privileges = privilegeStore.getPrivileges(guestName);
    SessionInfo info = new SessionInfo();
    info.setPrivileges(privileges);
    info.setUsername(guestName);
    return info;
  }
}
