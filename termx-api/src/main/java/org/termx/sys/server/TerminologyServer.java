package org.termx.sys.server;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class TerminologyServer {
  private Long id;
  private String code;
  private LocalizedName names;
  private String rootUrl;
  private List<TerminologyServerHeader> headers;
  private TerminologyServerAuthConfig authConfig;
  private List<String> kind;
  private boolean active;
  private boolean currentInstallation;

  private String accessInfo;
  private List<String> usage;
  private List<AuthoritativeResource> authoritative;
  private List<AuthoritativeResource> authoritativeValuesets;
  private List<AuthoritativeResource> authoritativeConceptmaps;
  private List<AuthoritativeResource> authoritativeStructuredefinitions;
  private List<AuthoritativeResource> authoritativeStructuremaps;
  private List<String> exclusions;
  private List<TerminologyServerFhirVersion> fhirVersions;
  private List<String> supportedOperations;
  private Integer cachePeriodHours;
  private String strategy;
  private Boolean open;
  private Boolean token;
  private Boolean oauthFlag;
  private Boolean smartFlag;
  private Boolean certFlag;

  @Getter
  @Setter
  public static class TerminologyServerHeader {
    private String key;
    private String value;
  }

  @Getter
  @Setter
  public static class TerminologyServerAuthConfig {
    private String accessTokenUrl;
    private String clientId;
    private String clientSecret;
  }

  @Getter
  @Setter
  @Introspected
  public static class AuthoritativeResource {
    private String url;
    private String status;
    private String version;
    private String name;

    /**
     * Converts to ecosystem.json canonical URL format: baseUrl|version?status=active
     * Following CanonicalUrlParser conventions: version after pipe, query params after ?
     */
    public String toEcosystemUrl() {
      String result = url;
      if (StringUtils.isNotEmpty(version)) {
        result += "|" + version;
      }
      if (StringUtils.isNotEmpty(status)) {
        result += "?status=" + status;
      }
      return result;
    }
  }

  @Getter
  @Setter
  @Introspected
  public static class TerminologyServerFhirVersion {
    private String version;
    private String url;
  }

  public TerminologyServer maskSensitiveData() {
    if (this.getHeaders() != null) {
      this.getHeaders().stream().filter(h -> h.getKey().equals("Authorization")).forEach(h -> h.setValue(null));
    }
    if (this.getAuthConfig() != null) {
      this.getAuthConfig().setClientSecret(null);
    }
    return this;
  }

  public TerminologyServer publicView() {
    return new TerminologyServer()
        .setId(this.id)
        .setCode(this.code)
        .setNames(this.names)
        .setRootUrl(this.rootUrl)
        .setKind(this.kind)
        .setActive(this.active)
        .setCurrentInstallation(this.currentInstallation)
        .setAccessInfo(this.accessInfo)
        .setUsage(this.usage)
        .setAuthoritative(this.authoritative)
        .setAuthoritativeValuesets(this.authoritativeValuesets)
        .setAuthoritativeConceptmaps(this.authoritativeConceptmaps)
        .setAuthoritativeStructuredefinitions(this.authoritativeStructuredefinitions)
        .setAuthoritativeStructuremaps(this.authoritativeStructuremaps)
        .setExclusions(this.exclusions)
        .setFhirVersions(this.fhirVersions)
        .setSupportedOperations(this.supportedOperations)
        .setCachePeriodHours(this.cachePeriodHours)
        .setStrategy(this.strategy)
        .setOpen(this.open)
        .setToken(this.token)
        .setOauthFlag(this.oauthFlag)
        .setSmartFlag(this.smartFlag)
        .setCertFlag(this.certFlag);
  }
}
