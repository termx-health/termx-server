package com.kodality.termserver.integration.common;

import com.kodality.termserver.commons.model.model.LocalizedName;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportConfiguration {
  private String uri;
  private String source;
  private String version;
  private LocalDate validFrom;
  private LocalDate validTo;
  private String codeSystem;
  private LocalizedName codeSystemName;
  private String codeSystemDescription;
  private String codeSystemVersionDescription;
}
