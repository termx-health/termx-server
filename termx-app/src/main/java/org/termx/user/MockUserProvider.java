package org.termx.user;

import org.termx.core.user.User;
import org.termx.core.user.UserProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.termx.auth.MockSessionProvider;
import org.termx.auth.MockSessionProvider.MockUser;

@Requires(property = "auth.mock.enabled", value = StringUtils.TRUE)
@Singleton
@RequiredArgsConstructor
public class MockUserProvider extends UserProvider {
  private final MockSessionProvider mockSessionProvider;

  @Override
  public List<User> getUsers() {
    return mockSessionProvider.getUsers().values().stream()
        .map(mu -> new User()
            .setSub(mu.username())
            .setName(mu.username())
            .setPrivileges(Set.copyOf(mu.privileges())))
        .toList();
  }
}
