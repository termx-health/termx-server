package org.termx.core.auth;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * The single source of truth for which request paths are public (need no session/privilege).
 *
 * <p>Both {@link AuthorizationFilter} and any downstream {@code SessionProvider} that grants
 * anonymous access to the same endpoints consume this bean, so the two layers can't drift — a path
 * added here is public everywhere, avoiding the "allowed by one layer, 403 by the other" trap.
 *
 * <p>The set is the built-in defaults plus {@code auth.public.endpoints}, plus any registered at
 * runtime via {@link #addPublicEndpoint} (e.g. the kefhir interceptor opening {@code /fhir}).
 */
@Singleton
public class PublicEndpointMatcher {
  private static final List<String> DEFAULT_PUBLIC = List.of("/health", "/info", "/public", "/metrics", "/prometheus");

  private final List<String> configuredEndpoints;

  public PublicEndpointMatcher(@Value("${auth.public.endpoints:[]}") List<String> configuredEndpoints) {
    // Copy into a mutable list so addPublicEndpoint works regardless of how the value was bound.
    this.configuredEndpoints = new ArrayList<>(configuredEndpoints != null ? configuredEndpoints : List.of());
  }

  /** Register an additional public prefix at runtime (e.g. once a module decides its routes are open). */
  public void addPublicEndpoint(String path) {
    configuredEndpoints.add(path);
  }

  /** Whether {@code path} is under a public prefix — an exact match or a `/`-delimited descendant. */
  public boolean isPublic(String path) {
    return Stream.concat(DEFAULT_PUBLIC.stream(), configuredEndpoints.stream())
        .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
  }
}
