package com.kodality.termserver.association;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class AssociationTypeQueryParams extends QueryParams {
  private String code;
  private String codeContains;
}
