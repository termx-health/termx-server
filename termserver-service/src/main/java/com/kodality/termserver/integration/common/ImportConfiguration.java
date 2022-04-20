package com.kodality.termserver.integration.common;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportConfiguration {
  private String codeSystem;
  private String version;
  private String uri;
  private String source;
  private LocalDate validFrom;
  private LocalDate validTo;
  private String codeSystemDescription;
  private String codeSystemVersionDescription;
}
