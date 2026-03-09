package org.termx.core.sys.email;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class EmailConfigStatus {
  private boolean configured;
  private boolean enabled;
  private List<String> missingParameters;
  private String from;
  private String smtpHost;
  private Integer smtpPort;
}
