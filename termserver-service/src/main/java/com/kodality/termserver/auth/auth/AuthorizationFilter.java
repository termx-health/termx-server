package com.kodality.termserver.auth.auth;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.netty.NettyHttpResponseFactory;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;

@Filter("/**")
@RequiredArgsConstructor
public class AuthorizationFilter implements HttpServerFilter {

  private final UserPrivilegeStore userPrivilegeStore;

  @Override
  public int getOrder() {
    return 2;
  }

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
    return Flowable.fromCallable(() -> checkPermissions(request)).switchMap(allowed -> {
      if (allowed) {
        return chain.proceed(request);
      }
      return Flowable.just(new NettyHttpResponseFactory().status(HttpStatus.FORBIDDEN));
    }).subscribeOn(Schedulers.io());
  }

  @SuppressWarnings("rawtypes")
  private boolean checkPermissions(HttpRequest<?> request) {
    Optional<SessionInfo> sessionInfo = request.getAttribute(SessionStore.KEY, SessionInfo.class);
    RouteMatch route = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
    if (!(route instanceof MethodBasedRouteMatch methodRoute) || !methodRoute.hasAnnotation(Authorized.class)) {
      return true;
    }
    if (sessionInfo.isEmpty()) {
      return false;
    }
    Collection<String> privileges = userPrivilegeStore.getPrivileges(sessionInfo.get());
    if (CollectionUtils.isEmpty(privileges)) {
      return false;
    }
    return methodRoute.getValue(Authorized.class, String[].class).map(authPrivileges -> hasAnyPrivilege(Arrays.asList(authPrivileges), privileges))
        .orElse(false);
  }

  public boolean hasAnyPrivilege(List<String> authPrivileges, Collection<String> userPrivileges) {
    return userPrivileges.contains("admin") || authPrivileges.stream().anyMatch(ap -> userPrivileges.stream().anyMatch(up -> privilegesMatch(ap, up)));
  }

  private boolean privilegesMatch(String authPrivilege, String userPrivilege) {
    if (authPrivilege.indexOf('*') == authPrivilege.length() - 1) {
      String start = authPrivilege.substring(0, authPrivilege.length() - 1);
      return userPrivilege.startsWith(start);
    }
    if (authPrivilege.indexOf('*') == 0) {
      String start = authPrivilege.substring(1);
      return userPrivilege.endsWith(start);
    }
    return userPrivilege.equals(authPrivilege);
  }

}
