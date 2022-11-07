package com.kodality.termserver.auth.smart;

import com.kodality.commons.client.HttpClient;
import com.kodality.commons.util.JsonUtil;
import com.kodality.zmei.fhir.resource.diagnostics.Observation;
import com.kodality.zmei.fhir.resource.individual.Patient;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import java.util.Map;
import java.util.UUID;

@Controller("/smart")
public class SmartLaunchController {

  @Get("launch")
  public Object launch(@QueryValue String iss, @QueryValue String launch) {
    String authUrl = JsonUtil.read(new HttpClient(iss).GET("/metadata", String.class).join(), "rest.security.extension[url='authorize'].valueUri");
    String xxx = new HttpClient(authUrl).GET("?" + HttpClient.toQueryParams(Map.of(
        "client_id", "terminology",
        "response_type", "code",
        "scope", "system/CodeSystem.read system/ValueSet.read",
        "redirect_uri", "https://terminology.kodality.dev/",
        "state", UUID.randomUUID(),
        "aud", iss,
        "launch", launch
    )), String.class).join();
    return xxx;
  }
}
