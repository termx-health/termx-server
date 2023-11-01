package com.kodality.termx.snomed.client;


import com.kodality.commons.client.HttpClient;
import com.kodality.termx.snomed.codesystem.SnomedCodeSystem;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class SnowstormInitClient {
  protected HttpClient client;
  protected String languages;

  public SnowstormInitClient() {
  }

  public SnowstormInitClient(String snomedUrl, String snomedBranch, Function<String, HttpClient> clientProvider) {
    client = clientProvider.apply(snomedUrl);
    if (snomedBranch.startsWith("MAIN/")) {
      languages = String.join(",", loadCodeSystem(snomedBranch.split("/")[1]).join().getLanguages().keySet());
    }
  }
  public CompletableFuture<SnomedCodeSystem> loadCodeSystem(String shortName) {
    return client.GET("codesystems/" + shortName, SnomedCodeSystem.class);
  }

}
