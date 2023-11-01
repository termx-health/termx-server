package com.kodality.termx.core.auth;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationValue;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.reactivestreams.Publisher;

@Filter("/**")
@RequiredArgsConstructor
public class AuthorizationFilter implements HttpServerFilter {
  @Value("${auth.public.endpoints:[]}")
  private List<String> publicEndpoints;
  private static final List<String> DEFAULT_PUBLIC = Arrays.asList("/health", "/info", "/public", "/metrics", "/prometheus");

  public void addPublicEndpoint(String path) {
    this.publicEndpoints.add(path);
  }

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
      return Flowable.just(new NettyHttpResponseFactory().status(HttpStatus.FORBIDDEN).body("forbidden"));
    }).subscribeOn(Schedulers.io());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private boolean checkPermissions(HttpRequest<?> request) {
    if (request.getMethod() == HttpMethod.OPTIONS) {
      return true;
    }

    if (Stream.concat(DEFAULT_PUBLIC.stream(), publicEndpoints.stream()).anyMatch(prefix -> startsWith(request.getPath(), prefix))) {
      return true;
    }

    RouteMatch route = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
    if (route == null) {
      return true; //let 404 handle it
    }

    if (route instanceof MethodBasedRouteMatch methodRoute && methodRoute.hasAnnotation(Authorized.class)) {
      Map<String, Object> routeParams = methodRoute.getVariableValues();
      List<String> requiredPrivileges = getPrivileges(methodRoute.getAnnotation(Authorized.class)).stream().map(p -> {
        if (StringUtils.countMatches(p, '.' ) == 1) {
          // take first param as resource, if present. * otherwise
          p = (routeParams.size() > 0 ? routeParams.values().iterator().next() : '*' ) + "." + p;
        }
        p = StringSubstitutor.replace(p, routeParams, "{", "}");
        return p;
      }).toList();
      if (requiredPrivileges.isEmpty()) {
        return true;
      }
      return request.getAttribute(SessionStore.KEY, SessionInfo.class).orElseThrow().hasAnyPrivilege(requiredPrivileges);
    }

    return false;
  }

  private List<String> getPrivileges(AnnotationValue<Authorized> aa) {
    if (aa == null) {
      return List.of();
    }
    if (aa.getValues().get("privilege") != null) {
      return List.of(aa.get("resource", String.class).orElseThrow() + "." + aa.getValues().get("privilege"));
    }
    return List.of(aa.get("value", String[].class).orElse(new String[]{}));
  }

  private boolean startsWith(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }
}
