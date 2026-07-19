package org.termx.auth;

import org.termx.core.auth.SessionInfo;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import java.util.Optional;

public abstract class SessionProvider {

  private static final String BEARER = "Bearer ";

  public abstract SessionInfo authenticate(HttpRequest<?> request);

  public int getOrder() {
    return 50;
  }

  /**
   * The bearer token, taken from the {@code Authorization} header or — for {@code GET} requests
   * only — a {@code ?token=} query parameter. The query fallback lets browser {@code <img>} loads
   * and file downloads (which cannot set headers) authenticate; it is limited to GET so that
   * mutations always require the header.
   */
  protected static Optional<String> bearerToken(HttpRequest<?> request) {
    Optional<String> header = request.getHeaders().getFirst("Authorization")
        .filter(a -> a.startsWith(BEARER))
        .map(a -> a.substring(BEARER.length()).trim());
    if (header.isPresent() || request.getMethod() != HttpMethod.GET) {
      return header;
    }
    return request.getParameters().getFirst("token").map(String::trim).filter(t -> !t.isEmpty());
  }
}
