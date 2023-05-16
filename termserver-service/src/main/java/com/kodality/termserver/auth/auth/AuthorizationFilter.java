package com.kodality.termserver.auth.auth;

import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.ResourceId;
import com.kodality.termserver.auth.SessionInfo;
import com.kodality.termserver.auth.SessionStore;
import io.micronaut.core.annotation.AnnotationMetadata;
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
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;

@Filter("/**")
@RequiredArgsConstructor
public class AuthorizationFilter implements HttpServerFilter {


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
      return Flowable.just(new NettyHttpResponseFactory().status(HttpStatus.FORBIDDEN).body("authorization"));
    }).subscribeOn(Schedulers.io());
  }

  @SuppressWarnings("rawtypes")
  private boolean checkPermissions(HttpRequest<?> request) {
    if (request.getMethod() == HttpMethod.OPTIONS) {
      return true;
    }
    Optional<SessionInfo> sessionInfo = request.getAttribute(SessionStore.KEY, SessionInfo.class);
    RouteMatch route = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
    if (!(route instanceof MethodBasedRouteMatch methodRoute) || !methodRoute.hasAnnotation(Authorized.class)) {
      return true;
    }
    if (sessionInfo.isEmpty() || CollectionUtils.isEmpty(sessionInfo.get().getPrivileges())) {
      return false;
    }

    AnnotationMetadata annotationMetadata = methodRoute.getAnnotationMetadata();
    return methodRoute.getValue(Authorized.class, String[].class).map(authPrivileges ->
        sessionInfo.get().hasAnyPrivilege(Arrays.asList(authPrivileges), annotationMetadata.stringValue(ResourceId.class))
    ).orElse(false);
  }
}
