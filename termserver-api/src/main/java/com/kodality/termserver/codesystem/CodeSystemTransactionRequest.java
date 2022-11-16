package com.kodality.termserver.codesystem;

import com.kodality.termserver.valueset.ValueSetTransactionRequest;
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
