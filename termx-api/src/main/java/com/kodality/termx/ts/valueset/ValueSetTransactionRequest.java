package com.kodality.termx.ts.valueset;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class ValueSetTransactionRequest {
  private ValueSet valueSet;
  private ValueSetVersion version;
}
