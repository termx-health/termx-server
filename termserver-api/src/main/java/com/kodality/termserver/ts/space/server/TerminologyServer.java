package com.kodality.termserver.ts.space.server;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
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
  private boolean active;
  private boolean currentInstallation;
}
