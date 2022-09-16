package com.kodality.termserver.valueset;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValueSetExpandRequest {
  private String valueSet;
  private String valueSetVersion;
  private ValueSetVersionRuleSet ruleSet;
}
