package com.kodality.termserver.auth;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpMethod;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;

@Filter("/**")
@RequiredArgsConstructor
public class AuthorizationFilter implements HttpServerFilter {
  private final CommonSessionProvider session;

  @Override
  public int getOrder() {
    return 10;
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
    if (request.getMethod() == HttpMethod.OPTIONS) {
      return true;
    }
    RouteMatch route = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
    if (!(route instanceof MethodBasedRouteMatch methodRoute) || !methodRoute.hasAnnotation(Authorized.class)) {
      return true;
    }

    Optional<CommonSessionInfo> sessionInfo = session.get(request);
    if (sessionInfo.isEmpty()) {
      return false;
    }
    if (CollectionUtils.isEmpty(sessionInfo.get().getPrivileges())) {
      return false;
    }
    return methodRoute.getValue(Authorized.class, String[].class).map(p -> sessionInfo.get().hasAnyPrivilege(p)).orElse(false);
  }


}
