package com.kodality.termserver.common;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class ImportConfiguration {
  @NonNull
  private String uri;
  private String source;
  @NonNull
  private String version;
  @NonNull
  private LocalDate validFrom;
  private LocalDate validTo;
  @NonNull
  private String codeSystem;
  private String baseCodeSystem;
  @NonNull
  private LocalizedName codeSystemName;
  private String codeSystemDescription;
  private String codeSystemVersionDescription;
}
