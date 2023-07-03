package com.kodality.termx.auth.auth;

import com.kodality.termx.auth.SessionInfo;
import com.kodality.termx.auth.SessionStore;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.netty.NettyHttpResponseFactory;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import java.util.Comparator;
import java.util.List;
import org.reactivestreams.Publisher;

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
    return Flowable.fromCallable(() -> authenticate(request)).switchMap(isAuthenticated -> {
      if (isAuthenticated) {
        return chain.proceed(request);
      }
      return Flowable.just(new NettyHttpResponseFactory().status(HttpStatus.UNAUTHORIZED));
    }).subscribeOn(Schedulers.io());
  }

  private boolean authenticate(HttpRequest<?> request) {
    if (request.getAttribute(SessionStore.KEY).isPresent()) {
      throw new IllegalStateException("well this is wrong");
    }
    for (SessionProvider provider : providers) {
      SessionInfo user = provider.authenticate(request);
      if (user != null) {
        request.setAttribute(SessionStore.KEY, user);
        setLang(user, request);
        return true;
      }
    }
    return false;
  }

  private void setLang(SessionInfo user, HttpRequest<?> request) {
    String lang = request.getHeaders().get(HttpHeaders.ACCEPT_LANGUAGE);
    if (lang == null || lang.contains(";")) {
      return;
    }
    user.setLang(lang);
  }
}

