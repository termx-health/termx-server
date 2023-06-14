package com.kodality.termserver.terminology.relatedartifacts;

import com.kodality.termserver.ts.relatedartifact.RelatedArtifact;
import com.kodality.termserver.ts.relatedartifact.RelatedArtifactRequest;
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
