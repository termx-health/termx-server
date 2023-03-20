package com.kodality.termserver.ts.association;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class AssociationTypeQueryParams extends QueryParams {
  private String code;
  private String codeContains;
  private List<String> permittedCodes;
}
