package org.termx.core.ts;

import com.kodality.commons.model.QueryResult;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class CodeSystemExternalProvider {
  public QueryResult<Concept> searchConcepts(String codeSystem, ConceptQueryParams params) {
    if (codeSystem == null || !codeSystem.equals(getCodeSystemId())) {
      return QueryResult.empty();
    }
    return searchConcepts(params);
  }

  public abstract QueryResult<Concept> searchConcepts(ConceptQueryParams params);

  public abstract String getCodeSystemId();

  /**
   * Total number of concepts this provider resolves for {@code codeSystem}. Providers whose concepts are
   * virtual (resolved at runtime rather than stored as code-system-version memberships) return a value here
   * so callers can show a meaningful count instead of the membership-based SQL count, which is always 0 for
   * such code systems. Returns empty when this provider does not handle {@code codeSystem}, leaving the
   * caller's stored count untouched.
   */
  public Optional<Integer> conceptCount(String codeSystem) {
    return Optional.empty();
  }
}
