package org.termx.auth.externalclient;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExternalClient {
  private String name;
  private String credential;
  private List<String> privileges;
}
