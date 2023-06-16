package com.kodality.termserver.snomed.client;


import com.kodality.commons.client.HttpClient;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.snomed.decriptionitem.SnomedDescriptionItemResponse;
import com.kodality.termserver.snomed.decriptionitem.SnomedDescriptionItemSearchParams;
import com.kodality.termserver.snomed.refset.SnomedRefsetMemberResponse;
import com.kodality.termserver.snomed.refset.SnomedRefsetResponse;
import com.kodality.termserver.snomed.refset.SnomedRefsetSearchParams;
import com.kodality.termserver.snomed.search.SnomedSearchResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class SnowstormClient {
  protected HttpClient client;
  private static final String branch = "MAIN/SNOMEDCT-EE/";

  public SnowstormClient() {
  }

  public SnowstormClient(String snomedUrl, Function<String, HttpClient> clientProvider) {
    client = clientProvider.apply(snomedUrl);
  }

  public CompletableFuture<SnomedConcept> loadConcept(String conceptId) {
    return client.GET("browser/" + branch + "concepts/" + conceptId, SnomedConcept.class);
  }

  public CompletableFuture<SnomedSearchResult<SnomedConcept>> queryConcepts(SnomedConceptSearchParams params) {
    String query = "?" + HttpClient.toQueryParams(params);
    return client.GET(branch + "concepts" + query, JsonUtil.getParametricType(SnomedSearchResult.class, SnomedConcept.class));
  }

  public CompletableFuture<List<SnomedConcept>> findConceptChildren(String conceptId) {
    return client.GET("browser/" + branch + "concepts/" + conceptId + "/children", JsonUtil.getListType(SnomedConcept.class));
  }

  public CompletableFuture<SnomedDescriptionItemResponse> findConceptDescriptions(SnomedDescriptionItemSearchParams params) {
    String query = "?" + HttpClient.toQueryParams(params);
    return client.GET("browser/" + branch + "descriptions" + query, SnomedDescriptionItemResponse.class);
  }

  public CompletableFuture<SnomedRefsetResponse> findRefsets(SnomedRefsetSearchParams params) {
    String query = "?" + HttpClient.toQueryParams(params);
    return client.GET("browser/" + branch + "members" + query, SnomedRefsetResponse.class);
  }

  public CompletableFuture<SnomedRefsetMemberResponse> findRefsetMembers(SnomedRefsetSearchParams params) {
    String query = "?" + HttpClient.toQueryParams(params);
    return client.GET(branch + "members" + query, SnomedRefsetMemberResponse.class);
  }
}
