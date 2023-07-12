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
  private List<String> kind;
  private boolean active;
  private boolean currentInstallation;
}
