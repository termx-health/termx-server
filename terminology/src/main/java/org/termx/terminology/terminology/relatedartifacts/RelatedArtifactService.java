package org.termx.terminology.terminology.relatedartifacts;

import org.termx.ts.relatedartifact.RelatedArtifact;
import org.termx.ts.relatedartifact.RelatedArtifactRequest;
import java.util.ArrayList;
import java.util.List;

public abstract class RelatedArtifactService {

  public List<RelatedArtifact> findRelatedArtifacts(RelatedArtifactRequest request) {
    if (!request.getType().equals(getResourceType())) {
      return new ArrayList<>();
    }
    return findRelatedArtifacts(request.getId());
  }

  public abstract String getResourceType();
  public abstract List<RelatedArtifact> findRelatedArtifacts(String id);

}
