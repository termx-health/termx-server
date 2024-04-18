package com.kodality.termx.core.ts;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class CodeSystemProvider {
  public abstract CodeSystem loadCodeSystem(String id);
  public abstract QueryResult<Concept> searchConcepts(ConceptQueryParams params);
  public abstract Optional<CodeSystemVersion> loadCodeSystemVersion(String id, String version);
  public abstract void activateVersion(String codeSystem, String version);
}
