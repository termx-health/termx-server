package org.termx.core.fhir;

import com.kodality.kefhir.core.api.resource.ResourceSearchHandler;
import com.kodality.kefhir.core.api.resource.ResourceStorage;
import com.kodality.kefhir.core.context.RequestSummaryContext;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.HistorySearchCriterion;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.core.util.SummaryProcessor;
import com.kodality.kefhir.structure.api.ResourceContent;
import java.util.List;

public abstract class BaseFhirResourceHandler implements ResourceStorage, ResourceSearchHandler {

  /**
   * Whether the current FHIR read / vread call asked for a lightweight summary
   * ({@code ?_summary=true|text|count}). Read from kefhir's {@link RequestSummaryContext}
   * thread-local that {@code DefaultFhirResourceServer} populates before invoking storage.
   * Storage subclasses use this to skip expensive loads — concept-version rows for
   * CodeSystem, association rows for ConceptMap, rule-set concepts for ValueSet — when
   * the response will be summary-stripped post-load anyway.
   *
   * <p>Returns {@code false} when no read is in scope (internal callers, batch loaders)
   * — the safe default that preserves the historical "load everything" behaviour.
   *
   * <p>Note: {@link SummaryProcessor.Mode#DATA} returns {@code false} on purpose — that
   * mode strips only the narrative {@code text}, not structural data, so the heavy load
   * is still needed.
   */
  protected static boolean isCurrentRequestLightweightSummary() {
    SummaryProcessor.Mode mode = RequestSummaryContext.get();
    return mode != null && mode != SummaryProcessor.Mode.FALSE && mode != SummaryProcessor.Mode.DATA;
  }

  public abstract ResourceVersion load(String id);

  public abstract String getResourceType();

  public abstract String getPrivilegeName();

  @Override
  public List<ResourceVersion> load(List<VersionId> ids) {
    //TODO: batch
    return ids.stream().map(this::load).toList();
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

  @Override
  public SearchResult search(SearchCriterion criteria) {
    throw new UnsupportedOperationException();
  }
}
