package com.kodality.termserver.codesystem;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class DesignationQueryParams extends QueryParams {
  private String id;
  private String name;
  private String language;
  private String designationKind;
  private Long designationTypeId;
  private String codeSystemEntityVersionId;
  private String codeSystem;

  private Long conceptId;
  private String conceptCode;
}
