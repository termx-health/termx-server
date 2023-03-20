package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemAssociationQueryParams extends QueryParams {
  private String codeSystemEntityVersionId;
  private String associationType;
}
