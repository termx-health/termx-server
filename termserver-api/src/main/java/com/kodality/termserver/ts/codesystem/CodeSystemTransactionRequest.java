package com.kodality.termserver.ts.codesystem;

import com.kodality.termserver.ts.valueset.ValueSetTransactionRequest;
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
