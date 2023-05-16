package com.kodality.termserver.ts.valueset;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class ValueSetExpandRequest {
  private String valueSet;
  private String valueSetVersion;
  private ValueSetVersionRuleSet ruleSet;
}
