package com.kodality.termserver.ts.codesystem;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class ConceptTransactionRequest {
  private String codeSystem;
  private String codeSystemVersion;
  private Concept concept;
  private CodeSystemEntityVersion entityVersion;
}
