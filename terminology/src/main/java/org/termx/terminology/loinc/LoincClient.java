package org.termx.terminology.loinc;

import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin client over an external LOINC FHIR terminology server (default the official {@code https://fhir.loinc.org},
 * a Smile CDR that requires HTTP Basic auth). The whole {@code Authorization} header value is taken verbatim
 * from {@code loinc.authentication} so an operator can drop in their own LOINC key, e.g. {@code "Basic xxxx"}.
 * Mirrors the SNOMED/Snowstorm integration pattern.
 */
@Slf4j
@Singleton
public class LoincClient {
  public static final String LOINC_URI = "http://loinc.org";
  private static final String FHIR_JSON = "application/fhir+json";

  private final String baseUrl;
  private final String authentication;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public LoincClient(@Value("${loinc.url:}") String url,
                     @Value("${loinc.authentication:}") String authentication) {
    String resolved = StringUtils.isNotEmpty(url) ? url : "https://fhir.loinc.org";
    this.baseUrl = resolved.endsWith("/") ? resolved.substring(0, resolved.length() - 1) : resolved;
    this.authentication = authentication;
  }

  public boolean isConfigured() {
    return StringUtils.isNotEmpty(authentication);
  }

  /** CodeSystem/$lookup for a LOINC code; returns the FHIR Parameters response, or null on any non-2xx/error. */
  public Parameters lookup(String code, String version) {
    String q = "/CodeSystem/$lookup?system=" + enc(LOINC_URI) + "&code=" + enc(code)
        + (StringUtils.isNotEmpty(version) ? "&version=" + enc(version) : "");
    return get(q);
  }

  private Parameters get(String path) {
    try {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
          .timeout(Duration.ofSeconds(30))
          .header("Accept", FHIR_JSON);
      if (StringUtils.isNotEmpty(authentication)) {
        b.header("Authorization", authentication);
      }
      HttpResponse<String> resp = http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        log.debug("LOINC {} -> {}", path, resp.statusCode());
        return null;
      }
      return FhirMapper.fromJson(resp.body(), Parameters.class);
    } catch (Exception e) {
      log.warn("LOINC request {} failed: {}", path, e.getMessage());
      return null;
    }
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
