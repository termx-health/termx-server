package com.kodality.termserver.codesystem;

import com.kodality.termserver.commons.model.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemEntityVersionQueryParams extends QueryParams {
  private Long codeSystemEntityId;
  private Long codeSystemVersionId;
  private String codeSystemVersion; //codeSystem|version
  private String status;
  private String code;
}
