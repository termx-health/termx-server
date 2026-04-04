package org.termx.ts.codesystem;

import com.kodality.commons.model.QueryResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConceptTreeSearchResult extends QueryResult<ConceptTreeItem> {
  private Integer matchedCount;
}
