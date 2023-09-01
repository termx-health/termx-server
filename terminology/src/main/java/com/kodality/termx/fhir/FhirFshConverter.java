package com.kodality.termx.fhir;

import com.kodality.commons.client.HttpClient;
import com.kodality.commons.client.HttpClientError;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.net.http.HttpRequest.Builder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Requires(property = "chef.url")
@Singleton
public class FhirFshConverter {
  private final HttpClient client;

  public FhirFshConverter(@Value("${chef.url}") String chefUrl) {
    this.client = new HttpClient(chefUrl) {
      @Override
      public Builder builder(String path) {
        Builder b = super.builder(path);
        b.setHeader("Content-Type", "application/json");
        return b;
      }
    };
  }

  public CompletableFuture<String> toFsh(String fhir) {
    return client.POST("/fhir2fsh", Map.of("fhir", List.of(fhir)))
        .thenApply(r -> (String) JsonUtil.toMap(r.body()).get("fsh"))
        .exceptionally(e -> {
          if (e.getCause() instanceof HttpClientError he && he.getResponse().body() != null && he.getResponse().body().startsWith("{")) {
            Map<String, Object> resp = JsonUtil.toMap(he.getResponse().body());
            if (resp.containsKey("fsh")) {
              //oh well. it gives 500 and still returns FSH.... ignore in this case
              return (String) JsonUtil.toMap(he.getResponse().body()).get("fsh");
            }
          }
          throw new RuntimeException(e);
        });
  }

}
