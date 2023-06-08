package com.kodality.termserver.terminology;

import com.kodality.termserver.auth.SessionStore;
import java.net.http.HttpRequest.Builder;

public class FhirClient extends com.kodality.zmei.fhir.client.FhirClient {
  public FhirClient(String baseUrl) {
    super(baseUrl);
  }

  @Override
  public Builder builder(String path) {
    Builder b = super.builder(path);
    b.header("Authorization", "Bearer " + SessionStore.require().getToken());
    return b;
  }
}
