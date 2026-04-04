package org.termx.terminology.terminology.codesystem.concept;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.termx.ts.codesystem.ConceptQueryParams;

@Getter
@RequiredArgsConstructor
class ConceptTreeQueryParams {
  private final ConceptQueryParams matchParams;
  private final String hierarchyType;
}
