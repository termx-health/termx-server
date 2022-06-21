package com.kodality.termserver.auth.auth;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SessionInfo {
  private List<String> privileges;
  private String lang;
}
