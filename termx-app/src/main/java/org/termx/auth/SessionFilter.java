package org.termx.auth;

import org.termx.core.auth.SessionInfo;
import org.termx.core.auth.SessionStore;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.netty.NettyHttpResponseFactory;
import java.util.Comparator;
import java.util.List;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import static java.util.stream.Collectors.toList;

@Filter("/**")
public class SessionFilter implements HttpServerFilter {

  private final List<SessionProvider> providers;

  public SessionFilter(List<SessionProvider> providers) {
    this.providers = providers.stream().sorted(Comparator.comparing(SessionProvider::getOrder)).collect(toList());
  }

  @Override
  public int getOrder() {
    return 1;
  }

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
    return Mono.fromCallable(() -> authenticate(request)).flatMap(isAuthenticated -> {
      if (isAuthenticated) {
        return Mono.from(chain.proceed(request));
      }
      return Mono.just(new NettyHttpResponseFactory().status(HttpStatus.UNAUTHORIZED));
    });
  }

  private boolean authenticate(HttpRequest<?> request) {
    if (request.getAttribute(SessionStore.KEY).isPresent()) {
      throw new IllegalStateException("well this is wrong");
    }
    for (SessionProvider provider : providers) {
      SessionInfo user = provider.authenticate(request);
      if (user != null) {
        deriveTaskPrivileges(user);
        request.setAttribute(SessionStore.KEY, user);
        setLang(user, request);
        return true;
      }
    }
    return false;
  }

  void deriveTaskPrivileges(SessionInfo session) {
    if (session.getPrivileges() == null) {
      return;
    }
    java.util.Set<String> derived = new java.util.HashSet<>(session.getPrivileges());

    // Keep admin unchanged. Use a literal string contains (not hasPrivilege) because
    // hasPrivilege("*.*.*") matches any 3-part privilege via wildcards, which would
    // short-circuit derivation for every authenticated user.
    if (session.getPrivileges().contains("*.*.*")) {
      session.setPrivileges(derived);
      return;
    }
    
    // Map resource types to context types (kebab-case)
    java.util.Map<String, String> resourceTypeToContextType = java.util.Map.of(
        "CodeSystem", "code-system",
        "ValueSet", "value-set",
        "MapSet", "map-set",
        "ConceptMap", "map-set"  // ConceptMap uses same context type as MapSet
    );
    
    // Derive Task privileges for each resource privilege
    for (String privilege : session.getPrivileges()) {
      String[] parts = splitPrivilege(privilege); // [resourceId, resourceType, action]
      if (parts == null || parts.length != 3) {
        continue;
      }
      
      String resourceId = parts[0];
      String resourceType = parts[1];
      String action = parts[2];
      
      String contextType = resourceTypeToContextType.get(resourceType);
      if (contextType == null) {
        continue; // Not a resource type we derive from
      }
      
      if ("triage".equals(action)) {
        derived.add(contextType + "#" + resourceId + ".Task.view");
      } else if ("edit".equals(action)) {
        derived.add(contextType + "#" + resourceId + ".Task.view");
        derived.add(contextType + "#" + resourceId + ".Task.edit");
      } else if ("publish".equals(action)) {
        derived.add(contextType + "#" + resourceId + ".Task.view");
        derived.add(contextType + "#" + resourceId + ".Task.edit");
        derived.add(contextType + "#" + resourceId + ".Task.publish");
      }
    }
    
    session.setPrivileges(derived);
  }

  private String[] splitPrivilege(String privilege) {
    // Split "resourceId.ResourceType.action" into [resourceId, resourceType, action]
    String[] parts = privilege.split("\\.");
    if (parts.length < 3) {
      return null;
    }
    return new String[]{
        String.join(".", java.util.Arrays.copyOfRange(parts, 0, parts.length - 2)),
        parts[parts.length - 2],
        parts[parts.length - 1]
    };
  }

  private void setLang(SessionInfo user, HttpRequest<?> request) {
    String lang = request.getHeaders().get(HttpHeaders.ACCEPT_LANGUAGE);
    if (lang == null || lang.contains(";")) {
      return;
    }
    user.setLang(lang);
  }
}

