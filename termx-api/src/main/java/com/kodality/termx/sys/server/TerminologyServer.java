package com.kodality.termx.sys.server;

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
        .setCurrentInstallation(this.currentInstallation);
  }
}
