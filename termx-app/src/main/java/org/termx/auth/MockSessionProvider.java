package org.termx.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.auth.SessionProvider;
import com.kodality.termx.core.auth.SessionInfo;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Requires(property = "auth.mock.enabled", value = StringUtils.TRUE)
@Slf4j
@Singleton
public class MockSessionProvider extends SessionProvider {
  private final String usersFile;
  private final String defaultUser;
  private Map<String, MockUser> users;

  public MockSessionProvider(
      @Value("${auth.mock.users-file:mock/users.json}") String usersFile,
      @Value("${auth.mock.default-user:admin}") String defaultUser) {
    this.usersFile = usersFile;
    this.defaultUser = defaultUser;
  }

  @PostConstruct
  void init() {
    this.users = loadUsers();
    log.info("Mock auth enabled with {} users from '{}', default user: '{}'", users.size(), usersFile, defaultUser);
  }

  @Override
  public int getOrder() {
    return 5;
  }

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    String bearer = request.getHeaders().getFirst("Authorization")
        .filter(h -> h.startsWith("Bearer "))
        .map(h -> h.substring("Bearer ".length()).trim())
        .orElse(null);
    MockUser user = bearer != null ? users.get(bearer) : null;
    if (user == null) {
      user = users.get(defaultUser);
    }
    if (user == null) {
      return null;
    }
    SessionInfo session = new SessionInfo();
    session.setUsername(user.username());
    session.setPrivileges(Set.copyOf(user.privileges()));
    session.setLang("en");
    return session;
  }

  private Map<String, MockUser> loadUsers() {
    String path = usersFile.startsWith("classpath:") ? usersFile.substring("classpath:".length()) : usersFile;
    try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
      if (is == null) {
        throw new IllegalStateException("Mock users file not found: " + usersFile);
      }
      String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      return JsonUtil.fromJson(json, new TypeReference<>() {});
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read mock users file: " + usersFile, e);
    }
  }

  public Map<String, MockUser> getUsers() {
    return users;
  }

  public record MockUser(String username, Set<String> privileges) {}
}
