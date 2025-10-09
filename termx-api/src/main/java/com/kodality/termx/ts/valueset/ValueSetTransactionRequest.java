package com.kodality.termx.ts.valueset;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class ValueSetTransactionRequest {
  @Valid
  private ValueSet valueSet;
  @Valid
  private ValueSetVersion version;
}
