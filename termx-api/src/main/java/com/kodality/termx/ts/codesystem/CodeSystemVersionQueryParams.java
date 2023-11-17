package com.kodality.termx.ts.codesystem;

import com.kodality.commons.model.QueryParams;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemVersionQueryParams extends QueryParams {
  private String codeSystem;
  private String codeSystemUri;
  private String codeSystemName;
  private String codeSystemTitle;
  private String codeSystemPublisher;
  private String codeSystemContent;
  private String codeSystemDescriptionContains;
  private String conceptCode;
  private List<String> permittedCodeSystems;
  private String ids;
  private String version;
  private String status;
  private LocalDate releaseDateLe;
  private LocalDate releaseDateGe;
  private LocalDate expirationDateLe;
  private LocalDate expirationDateGe;
}
