package com.kodality.termserver.auth.auth;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Singleton;

@Singleton
public class PublicSessionProvider extends SessionProvider {
  private static final List<String> DEFAULT_PUBLIC = Arrays.asList("/health", "/info");

  @Value("${auth.public.endpoints:[]}")
  private List<String> publicEndpoints;

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    if (Stream.concat(DEFAULT_PUBLIC.stream(), publicEndpoints.stream()).anyMatch(prefix -> startsWith(request.getPath(), prefix))) {
      return new SessionInfo();
    }
    return null;
  }

  private boolean startsWith(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }

  @Override
  public int getOrder() {
    return 5;
  }
}
