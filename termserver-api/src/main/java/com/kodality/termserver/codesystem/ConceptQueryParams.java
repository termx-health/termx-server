package com.kodality.termserver.codesystem;

import com.kodality.commons.model.QueryParams;
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
  private String valueSet;
  private String valueSetVersion;
}
