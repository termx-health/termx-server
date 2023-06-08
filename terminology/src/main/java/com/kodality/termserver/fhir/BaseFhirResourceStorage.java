package com.kodality.termserver.fhir;

import com.kodality.kefhir.core.api.resource.ResourceStorage;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.HistorySearchCriterion;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import java.util.List;

public abstract class BaseFhirResourceStorage implements ResourceStorage {

  public abstract SearchResult search(SearchCriterion criteria);
  public abstract ResourceVersion load(String id);

  @Override
  public List<ResourceVersion> load(List<ResourceId> ids) {
    //TODO: batch
    return ids.stream().map(id -> load(new VersionId(id))).toList();
  }

  @Override
  public ResourceVersion load(VersionId id) {
    return load(id.getResourceId());
  }

  @Override
  public ResourceVersion save(ResourceId id, ResourceContent content) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ResourceVersion saveForce(ResourceId id, ResourceContent content) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete(ResourceId id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ResourceVersion> loadHistory(HistorySearchCriterion criteria) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String generateNewId() {
    throw new UnsupportedOperationException();
  }
}
