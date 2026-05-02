package org.termx.auth;

import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.termx.core.auth.SessionInfo;

/**
 * Local-dev session provider, activated by {@code auth.dev.allowed=true}.
 *
 * <p>By default the yupi session is granted the full set of action wildcards
 * ({@code *.*.view}, {@code *.*.triage}, {@code *.*.edit}, {@code *.*.publish}),
 * which is effectively admin-equivalent for the privilege-matching algorithm.
 *
 * <p>For QA / migration testing the privilege set can be overridden via the
 * {@code auth.dev.yupi.privileges} property. The value is a comma-separated
 * list of dotted privilege strings. Common testing presets:
 * <ul>
 *   <li>{@code *.*.*} -- true Admin (short-circuits Task derivation)</li>
 *   <li>{@code *.*.view} -- view-only across all resources (use to verify
 *       Phase A Q5: download / comment sections must be hidden)</li>
 *   <li>{@code *.*.view,*.*.triage} -- view + triage (downloads + comments
 *       visible, no edit)</li>
 *   <li>{@code icd-10.CodeSystem.view} -- scoped view-only on a single
 *       resource</li>
 * </ul>
 *
 * <p>Example: {@code ./gradlew :termx-app:run -Pdev -PyupiPrivileges='*.*.view'}
 *
 * <p>Per-request overrides via the {@code Authorization: Bearer yupi<json>}
 * header still work and take precedence over the configured default.
 */
@Requires(property = "auth.dev.allowed", value = StringUtils.TRUE)
@Slf4j
@Singleton
public class YupiSessionProvider extends SessionProvider {
  private static final String BEARER_YUPI = "Bearer yupi";
  static final Set<String> DEFAULT_PRIVILEGES =
      Set.of("*.*.view", "*.*.triage", "*.*.edit", "*.*.publish");

  private final Set<String> configuredPrivileges;

  public YupiSessionProvider(
      @Property(name = "auth.dev.yupi.privileges") @Nullable String configuredPrivileges) {
    this.configuredPrivileges = parsePrivileges(configuredPrivileges);
    if (!this.configuredPrivileges.equals(DEFAULT_PRIVILEGES)) {
      log.info("yupi default session overridden via auth.dev.yupi.privileges: {}",
          this.configuredPrivileges);
    }
  }

  @Override
  public int getOrder() {
    // Must run before MockSessionProvider (order = 5), which would otherwise
    // intercept the `Bearer yupi` token, fail to find a matching mock user,
    // and fall back to its `default-user` (typically `admin` with `*.*.*`).
    // Yupi is selective (only matches `Bearer yupi*`), so taking priority is safe.
    return 3;
  }

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    if (request.getHeaders().getFirst("Authorization").map(auth -> auth.startsWith(BEARER_YUPI)).orElse(false)) {
      String sessionInfo = request.getHeaders().getFirst("Authorization").get().substring(BEARER_YUPI.length());
      if (!sessionInfo.isEmpty()) {
        return JsonUtil.fromJson(sessionInfo, SessionInfo.class);
      }
      return yupiDroopy();
    }
    return null;
  }

  private SessionInfo yupiDroopy() {
    SessionInfo s = new SessionInfo();
    s.setUsername("yupi");
    s.setLang("en");
    s.setPrivileges(configuredPrivileges);
    return s;
  }

  static Set<String> parsePrivileges(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_PRIVILEGES;
    }
    Set<String> parsed = Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
    return parsed.isEmpty() ? DEFAULT_PRIVILEGES : Set.copyOf(parsed);
  }
}
