package org.termx.sys.server;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
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
  private List<String> exclusions;
  private List<TerminologyServerFhirVersion> fhirVersions;
  private List<String> supportedOperations;

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
        .setExclusions(this.exclusions)
        .setFhirVersions(this.fhirVersions)
        .setSupportedOperations(this.supportedOperations);
  }
}
