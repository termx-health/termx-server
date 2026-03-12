package org.termx.ts.valueset;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class ValueSetRuleExpandRequest extends ValueSetExpandRequest {
  private boolean inactiveConcepts;
  private ValueSetVersionRuleSet.ValueSetVersionRule rule;
}
