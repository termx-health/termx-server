package com.kodality.termserver.codesystem;

import com.kodality.commons.model.QueryParams;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ConceptQueryParams extends QueryParams {
  private String code;
  private String codeContains;
  private String codeSystem;
  private String codeSystemUri;
  private String codeSystemVersion;
  private LocalDate codeSystemVersionReleaseDateLe;
  private LocalDate codeSystemVersionReleaseDateGe;
  private LocalDate codeSystemVersionExpirationDateLe;
  private LocalDate codeSystemVersionExpirationDateGe;
  private String codeSystemEntityStatus;
  private String valueSet;
  private String valueSetVersion;
}
