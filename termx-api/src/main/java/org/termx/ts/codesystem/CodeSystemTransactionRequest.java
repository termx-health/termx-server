package org.termx.ts.codesystem;

import org.termx.ts.valueset.ValueSetTransactionRequest;
import io.micronaut.core.annotation.Introspected;
import java.util.List;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class CodeSystemTransactionRequest {
  @Valid
  private CodeSystem codeSystem;
  @Valid
  private CodeSystemVersion version;
  private List<EntityProperty> properties;

  private ValueSetTransactionRequest valueSet;
}
