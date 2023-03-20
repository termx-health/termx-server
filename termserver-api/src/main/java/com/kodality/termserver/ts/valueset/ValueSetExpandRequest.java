package com.kodality.termserver.ts.valueset;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValueSetExpandRequest {
  private String valueSet;
  private String valueSetVersion;
  private ValueSetVersionRuleSet ruleSet;
}
