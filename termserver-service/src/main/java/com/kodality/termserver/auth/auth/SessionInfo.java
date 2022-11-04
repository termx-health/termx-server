package com.kodality.termserver.auth.auth;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SessionInfo {
  private String token;
  private String username;
  private Set<String> privileges;
  private String lang;

  private String provider;
  private Map<String, String> providerProperties;

  public interface AuthenticationProvider {
    String sso = "sso";
    String smart = "smart-on-fhir";
  }
}
