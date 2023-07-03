package com.kodality.termx.snomed.client;

import com.kodality.commons.client.HttpClient;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import java.net.http.HttpRequest.Builder;
import java.util.Base64;
import java.util.Optional;

@Requires(notEnv = "test")
@Factory
public class SnowstormClientFactory {

  @Value("${snowstorm.url}")
  private String snowstormUrl;

  @Value("${snowstorm.user}")
  private Optional<String> snowstormUser;

  @Value("${snowstorm.password}")
  private Optional<String> snowstormPassword;

  @Bean
  public SnowstormClient getSnowstormClient() {
    return new SnowstormClient(snowstormUrl, baseUrl -> new HttpClient(baseUrl) {
      @Override
      public Builder builder(String path) {
        Builder builder = super.builder(path);
        if (snowstormUser.isPresent() && snowstormPassword.isPresent()) {
          builder.setHeader(HttpHeaders.AUTHORIZATION, basicAuth(snowstormUser.get(), snowstormPassword.get()));
        }
        return builder;
      }
    });
  }

  private static String basicAuth(String username, String password) {
    return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
  }
}
