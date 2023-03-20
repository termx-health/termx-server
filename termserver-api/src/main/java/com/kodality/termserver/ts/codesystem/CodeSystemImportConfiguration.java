package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.model.LocalizedName;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemImportConfiguration {
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
