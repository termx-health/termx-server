package com.kodality.termserver.valueset;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetVersionRuleQueryParams extends QueryParams {
  private String codeSystem;
  private String codeSystemVersionIds;
  private String valueSet;
  private String valueSetVersionIds;
}
