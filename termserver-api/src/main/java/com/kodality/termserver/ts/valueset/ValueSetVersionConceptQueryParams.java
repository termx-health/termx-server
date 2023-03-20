package com.kodality.termserver.ts.valueset;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetVersionConceptQueryParams extends QueryParams {
  private Long valueSetVersionId;
  private String conceptCode;
  private String codeSystemUri;
  private String codeSystemVersion;
  private boolean decorated;
}
