package com.kodality.termserver.codesystem;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemEntityVersionQueryParams extends QueryParams {
  private String code;
  private String status;
  private String codeSystem;
  private Long codeSystemEntityId;
  private Long codeSystemVersionId;
  private String codeSystemVersion;
}
