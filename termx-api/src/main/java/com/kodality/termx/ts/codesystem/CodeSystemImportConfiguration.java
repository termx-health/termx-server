package com.kodality.termx.ts.codesystem;

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
  private String publisher;
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
  private LocalizedName codeSystemDescription;
  private LocalizedName codeSystemVersionDescription;
  private String status;

  private String sourceUrl;

  private boolean generateValueSet;
  private boolean cleanRun;
  private boolean cleanConceptRun;
}
