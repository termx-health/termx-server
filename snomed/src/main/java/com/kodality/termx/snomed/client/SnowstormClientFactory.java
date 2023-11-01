package com.kodality.termx.snomed.client;

import com.kodality.commons.client.HttpClient;
import com.kodality.termx.core.http.BinaryHttpClient;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import java.net.http.HttpRequest.Builder;
import java.util.Base64;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

@Requires(notEnv = "test")
@Factory
public class SnowstormClientFactory {

  @Value("${snowstorm.url}")
  private String snowstormUrl;

  @Value("${snowstorm.branch}")
  private Optional<String> snowstormBranch;

  @Value("${snowstorm.user}")
  private Optional<String> snowstormUser;

  @Value("${snowstorm.password}")
  private Optional<String> snowstormPassword;

  @Bean
  public SnowstormInitClient getSnowstormInitClient() {
    return new SnowstormInitClient(snowstormUrl, snowstormBranch.orElse("MAIN"), baseUrl -> new HttpClient(baseUrl) {
      @Override
      public Builder builder(String path) {
        return SnowstormClientFactory.builder(super.builder(path), snowstormUser, snowstormPassword, Optional.empty());
      }
    });
  }

  @Bean
  public SnowstormClient getSnowstormClient() {
    SnowstormInitClient initClient = getSnowstormInitClient();
    return new SnowstormClient(snowstormUrl, snowstormBranch.orElse("MAIN"), baseUrl -> Pair.of(new HttpClient(baseUrl) {
      @Override
      public Builder builder(String path) {
        return SnowstormClientFactory.builder(super.builder(path), snowstormUser, snowstormPassword, Optional.ofNullable(initClient.languages));
      }
    }, new BinaryHttpClient(baseUrl) {
      @Override
      public Builder builder(String path) {
        return SnowstormClientFactory.builder(super.builder(path), snowstormUser, snowstormPassword, Optional.ofNullable(initClient.languages));
      }
    }));
  }

  private static Builder builder(Builder builder, Optional<String> snowstormUser, Optional<String> snowstormPassword, Optional<String> languages) {
    if (snowstormUser.isPresent() && snowstormPassword.isPresent()) {
      builder.setHeader(HttpHeaders.AUTHORIZATION, basicAuth(snowstormUser.get(), snowstormPassword.get()));
    }
    if (languages.isPresent() && snowstormPassword.isPresent()) {
      builder.setHeader(HttpHeaders.ACCEPT_LANGUAGE, languages.get());
    }
    return builder;
  }

  private static String basicAuth(String username, String password) {
    return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
  }
}
