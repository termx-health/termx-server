package com.kodality.termx.ts.codesystem;

import com.kodality.termx.ts.valueset.ValueSetTransactionRequest;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class CodeSystemTransactionRequest {
  private CodeSystem codeSystem;
  private CodeSystemVersion version;
  private List<EntityProperty> properties;

  private ValueSetTransactionRequest valueSet;
}
