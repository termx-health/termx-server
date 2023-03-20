package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemEntityVersionQueryParams extends QueryParams {
  private String ids;
  private String code;
  private String codeContains;
  private String descriptionContains;
  private String textContains;
  private String status;
  private String codeSystem;
  private List<String> permittedCodeSystems;
  private Long codeSystemEntityId;
  private String codeSystemEntityIds;
  private Long codeSystemVersionId;
  private String codeSystemVersion;
  private String codeSystemUri;
}
