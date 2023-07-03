package com.kodality.termx.snomed.client;


import com.kodality.commons.client.HttpClient;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.snomed.branch.SnomedBranch;
import com.kodality.termx.snomed.branch.SnomedBranchRequest;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termx.snomed.decriptionitem.SnomedDescriptionItemResponse;
import com.kodality.termx.snomed.decriptionitem.SnomedDescriptionItemSearchParams;
import com.kodality.termx.snomed.description.SnomedDescriptionSearchParams;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.refset.SnomedRefsetMemberResponse;
import com.kodality.termx.snomed.refset.SnomedRefsetResponse;
import com.kodality.termx.snomed.refset.SnomedRefsetSearchParams;
import com.kodality.termx.snomed.search.SnomedSearchResult;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
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

  public CompletableFuture<List<SnomedBranch>> loadBranches() {
    return client.GET("branches", JsonUtil.getListType(SnomedBranch.class));
  }

  public CompletableFuture<SnomedBranch> loadBranch(String path) {
    return client.GET("branches/" + path, SnomedBranch.class);
  }

  public CompletableFuture<SnomedBranch> createBranch(SnomedBranchRequest request) {
    return client.POST("branches", request, SnomedBranch.class);
  }

  public CompletableFuture<SnomedBranch> updateBranch(String path, SnomedBranchRequest request) {
    return client.PUT("branches/" + path, request, SnomedBranch.class);
  }

  public CompletableFuture<HttpResponse<String>> deleteBranch(String path) {
    return client.DELETE("admin/" + path + "/actions/hard-delete");
  }

  public CompletableFuture<HttpResponse<String>> lockBranch(String path, Map<String, String> params) {
    String query = "?" + HttpClient.toQueryParams(params);
    return client.POST("branches/" + path + "/actions/lock" + query, null);
  }

  public CompletableFuture<HttpResponse<String>> unlockBranch(String path) {
    return client.POST("branches/" + path + "/actions/unlock", null);
  }

  public CompletableFuture<Object> branchIntegrityCheck(String path) {
    if ("MAIN".equals(path)) {
      return client.POST(path + "/integrity-check-full", null, Object.class);
    }
    return client.POST(path + "/integrity-check", null, Object.class);
  }

  public CompletableFuture<SnomedConcept> loadConcept(String conceptId) {
    return client.GET("browser/" + branch + "concepts/" + conceptId, SnomedConcept.class);
  }

  public CompletableFuture<SnomedSearchResult<SnomedConcept>> queryConcepts(SnomedConceptSearchParams params) {
    return queryConcepts(branch, params);
  }

  public CompletableFuture<SnomedSearchResult<SnomedConcept>> queryConcepts(String path, SnomedConceptSearchParams params) {
    String query = "?" + HttpClient.toQueryParams(params);
    return client.GET(path + "concepts" + query, JsonUtil.getParametricType(SnomedSearchResult.class, SnomedConcept.class));
  }

  public CompletableFuture<List<SnomedConcept>> findConceptChildren(String conceptId) {
    return client.GET("browser/" + branch + "concepts/" + conceptId + "/children", JsonUtil.getListType(SnomedConcept.class));
  }

  public CompletableFuture<SnomedDescriptionItemResponse> findConceptDescriptions(SnomedDescriptionItemSearchParams params) {
    String query = "?" + HttpClient.toQueryParams(params);
    return client.GET("browser/" + branch + "descriptions" + query, SnomedDescriptionItemResponse.class);
  }

  public CompletableFuture<SnomedSearchResult<SnomedDescription>> queryDescriptions(SnomedDescriptionSearchParams params) {
    return queryDescriptions(branch, params);
  }

  public CompletableFuture<SnomedSearchResult<SnomedDescription>> queryDescriptions(String path, SnomedDescriptionSearchParams params) {
    String query = "?" + HttpClient.toQueryParams(params);
    return client.GET(path + "descriptions" + query, JsonUtil.getParametricType(SnomedSearchResult.class, SnomedDescription.class));
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
