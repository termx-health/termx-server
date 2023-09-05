package com.kodality.termx.snomed.client;


import com.kodality.commons.client.HttpClient;
import com.kodality.commons.client.MultipartBodyPublisher;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.http.BinaryHttpClient;
import com.kodality.termx.snomed.branch.SnomedBranch;
import com.kodality.termx.snomed.branch.SnomedBranchRequest;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termx.snomed.decriptionitem.SnomedDescriptionItemResponse;
import com.kodality.termx.snomed.decriptionitem.SnomedDescriptionItemSearchParams;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.description.SnomedDescriptionSearchParams;
import com.kodality.termx.snomed.refset.SnomedRefsetMemberResponse;
import com.kodality.termx.snomed.refset.SnomedRefsetResponse;
import com.kodality.termx.snomed.refset.SnomedRefsetSearchParams;
import com.kodality.termx.snomed.rf2.SnomedExportJob;
import com.kodality.termx.snomed.rf2.SnomedExportRequest;
import com.kodality.termx.snomed.rf2.SnomedImportJob;
import com.kodality.termx.snomed.rf2.SnomedImportRequest;
import com.kodality.termx.snomed.search.SnomedSearchResult;
import io.micronaut.http.MediaType;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;

public class SnowstormClient {
  protected HttpClient client;
  protected BinaryHttpClient binaryHttpClient;
  protected String branch;

  public SnowstormClient() {
  }

  public SnowstormClient(String snomedUrl, String snomedBranch, Function<String, Pair<HttpClient, BinaryHttpClient>> clientProvider) {
    client = clientProvider.apply(snomedUrl).getLeft();
    binaryHttpClient = clientProvider.apply(snomedUrl).getRight();
    branch = snomedBranch + "/";
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

  public CompletableFuture<String> createExportJob(SnomedExportRequest request) {
    return client.POST("exports", request).thenApply(resp -> resp.headers().firstValue("location").map(l -> {
      String[] location = l.split("/");
      return location[location.length - 1];
    }).orElseThrow());
  }

  public CompletableFuture<SnomedExportJob> loadExportJob(String jobId) {
    return client.GET("exports/" + jobId, SnomedExportJob.class);
  }

  public byte[] getRF2File(String jobId) {
    return binaryHttpClient.GET("exports/" + jobId + "/archive").body();
  }

  public CompletableFuture<String> createImportJob(SnomedImportRequest request) {
    return client.POST("imports", request).thenApply(resp -> resp.headers().firstValue("location").map(l -> {
      String[] location = l.split("/");
      return location[location.length - 1];
    }).orElseThrow());
  }

  public CompletableFuture<SnomedImportJob> loadImportJob(String jobId) {
    return client.GET("imports/" + jobId, SnomedImportJob.class);
  }

  public CompletableFuture<HttpResponse<String>> uploadRF2File(String jobId, byte[] file) throws FileNotFoundException {
    MultipartBodyPublisher publisher = new MultipartBodyPublisher()
        .addPart("file", () -> new ByteArrayInputStream(file), "file", MediaType.APPLICATION_OCTET_STREAM);
    HttpRequest request = client.builder("imports/" + jobId + "/archive").POST(publisher.build())
        .header("Content-Type", "multipart/form-data; boundary=" + publisher.getBoundary())
        .build();
    return client.executeAsync(request);
  }

  public CompletableFuture<SnomedConcept> loadConcept(String conceptId) {
    return loadConcept(branch, conceptId);
  }

  public CompletableFuture<SnomedConcept> loadConcept(String path, String conceptId) {
    return client.GET("browser/" + path + "concepts/" + conceptId, SnomedConcept.class);
  }

  public CompletableFuture<HttpResponse<String>> updateConcept(String path, SnomedConcept concept) {
    return client.PUT("browser/" + path + "concepts/" +  concept.getConceptId(), concept);
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

  public CompletableFuture<HttpResponse<String>> deleteDescription(String path, String descriptionId) {
    return client.DELETE(path + "descriptions/" + descriptionId);
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
