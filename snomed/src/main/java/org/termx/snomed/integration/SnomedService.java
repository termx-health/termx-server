package org.termx.snomed.integration;

import com.kodality.commons.client.HttpClientError;
import com.kodality.commons.exception.ApiException;
import org.termx.snomed.ApiError;
import org.termx.snomed.client.SnowstormClient;
import org.termx.snomed.codesystem.SnomedCodeSystem;
import org.termx.snomed.concept.SnomedConcept;
import org.termx.snomed.concept.SnomedConceptSearchParams;
import org.termx.snomed.description.SnomedDescription;
import org.termx.snomed.description.SnomedDescriptionSearchParams;
import org.termx.snomed.rf2.SnomedImportRequest;
import org.termx.snomed.rf2.SnomedImportTracking;
import org.termx.snomed.search.SnomedSearchResult;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.io.FileNotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class SnomedService {
  private final SnowstormClient snowstormClient;
  private final SnomedImportTrackingRepository trackingRepository;

  private static final int MAX_COUNT = 9999;
  private static final int MAX_CONCEPT_COUNT = 100;
  private final Pattern BRANCH_REGEX = Pattern.compile("^[A-Z0-9/-]+$");

  public List<SnomedConcept> loadConcepts(List<String> ids, String branch) {
    return loadConcepts(ids, null, branch);
  }

  public List<SnomedConcept> loadConcepts(List<String> ids) {
    return loadConcepts(ids, null, null);
  }

  public List<SnomedConcept> loadConcepts(List<String> ids, Boolean active, String branch) {
    List<SnomedConcept> concepts = new ArrayList<>();
    IntStream.range(0, (ids.size() + MAX_CONCEPT_COUNT - 1) / MAX_CONCEPT_COUNT)
        .mapToObj(i -> ids.subList(i * MAX_CONCEPT_COUNT, Math.min(ids.size(), (i + 1) * MAX_CONCEPT_COUNT))).forEach(batch -> {
          SnomedConceptSearchParams params = new SnomedConceptSearchParams();
          params.setConceptIds(batch);
          params.setLimit(batch.size());
          params.setActive(active);
          params.setBranch(branch);
          concepts.addAll(searchConcepts(params));
        });
    return concepts;
  }

  public List<SnomedConcept> searchConcepts(SnomedConceptSearchParams params) {
    if (params.isAll()) {

      params.setLimit(1);
      SnomedSearchResult<SnomedConcept> result = snowstormClient.queryConcepts(params).join();
      Integer total = result.getTotal();

      List<SnomedConcept> snomedConcepts = new ArrayList<>();
      int bound = (total + MAX_COUNT - 1) / MAX_COUNT;
      for (int i = 0; i < bound; i++) {
        params.setLimit(MAX_COUNT);
        params.setSearchAfter(i == 0 ? null : result.getSearchAfter());
        result = snowstormClient.queryConcepts(params).join();
        snomedConcepts.addAll(result.getItems());
      }
      return snomedConcepts;
    }
    return snowstormClient.queryConcepts(params).join().getItems();
  }

  public List<SnomedDescription> loadDescriptions(List<String> conceptIds) {
    return loadDescriptions(null, conceptIds);
  }

  public List<SnomedDescription> loadDescriptions(String branch, List<String> conceptIds) {
    List<SnomedDescription> descriptions = new ArrayList<>();
    IntStream.range(0, (conceptIds.size() + MAX_CONCEPT_COUNT - 1) / MAX_CONCEPT_COUNT)
        .mapToObj(i -> conceptIds.subList(i * MAX_CONCEPT_COUNT, Math.min(conceptIds.size(), (i + 1) * MAX_CONCEPT_COUNT))).forEach(batch -> {
          SnomedDescriptionSearchParams params = new SnomedDescriptionSearchParams();
          params.setConceptIds(batch);
          params.setAll(true);
          descriptions.addAll(searchDescriptions(branch, params));
        });
    return descriptions;
  }

  public List<SnomedDescription> searchDescriptions(SnomedDescriptionSearchParams params) {
    return searchDescriptions(null, params);
  }

  public List<SnomedDescription> searchDescriptions(String branch, SnomedDescriptionSearchParams params) {
    if (params.isAll()) {

      params.setLimit(1);
      SnomedSearchResult<SnomedDescription> result = snowstormClient.queryDescriptions(branch, params).join();
      Integer total = result.getTotal();

      List<SnomedDescription> snomedDescriptions = new ArrayList<>();
      int bound = (total + MAX_COUNT - 1) / MAX_COUNT;
      for (int i = 0; i < bound; i++) {
        params.setLimit(MAX_COUNT);
        params.setSearchAfter(i == 0 ? null : result.getSearchAfter());
        result = snowstormClient.queryDescriptions(branch, params).join();
        snomedDescriptions.addAll(result.getItems());
      }
      return snomedDescriptions;
    }
    return snowstormClient.queryDescriptions(branch, params).join().getItems();
  }

  @Transactional
  public Map<String, String> importRF2File(SnomedImportRequest req, byte[] importFile) {
    String jobId;
    try {
      jobId = snowstormClient.createImportJob(req).join();
    } catch (CompletionException e) {
      throw rethrowSnowstormImportFailure(e);
    }
    try {
      snowstormClient.uploadRF2File(jobId, importFile).join();
    } catch (FileNotFoundException e) {
      log.warn("SNOMED RF2 upload to Snowstorm failed: {}", e.getMessage());
    } catch (CompletionException e) {
      throw rethrowSnowstormImportFailure(e);
    }

    SnomedImportTracking tracking = new SnomedImportTracking()
        .setSnowstormJobId(jobId)
        .setBranchPath(req.getBranchPath())
        .setType(req.getType())
        .setStatus("RUNNING")
        .setStarted(OffsetDateTime.now())
        .setNotified(false);
    trackingRepository.save(tracking);

    log.info("Created SNOMED import tracking record for Snowstorm job: {}", jobId);

    return Map.of("jobId", jobId);
  }

  /**
   * Convert a CompletableFuture failure from the Snowstorm client into a clean ApiException.
   * Logs a one-line warn (no stack) so the dev-server log isn't polluted by the full Netty
   * trace every time Snowstorm rejects an import (e.g. auth misconfiguration). The frontend
   * still receives a structured error with the upstream status and URL.
   */
  private ApiException rethrowSnowstormImportFailure(CompletionException e) {
    Throwable cause = e.getCause() != null ? e.getCause() : e;
    if (cause instanceof HttpClientError hce) {
      int status = hce.getResponse() == null ? 0 : hce.getResponse().statusCode();
      String url = hce.getRequest() == null ? "(unknown)" : hce.getRequest().uri().toString();
      log.warn("Snowstorm import call failed: HTTP {} {}", status, url);
      return ApiError.SN201.toApiException(Map.of("status", status, "url", url));
    }
    // Unknown async failure — keep the full stack so it surfaces as a real bug.
    throw e;
  }

  public List<SnomedCodeSystem> loadCodeSystems() {
    List<SnomedCodeSystem> codeSystems = snowstormClient.loadCodeSystems().join().getItems();
    codeSystems.forEach(cs -> cs.setVersions(snowstormClient.loadCodeSystemVersions(cs.getShortName()).join().getItems()));
    return codeSystems;
  }

  public SnomedCodeSystem loadCodeSystem(String shortName) {
    SnomedCodeSystem codeSystem = snowstormClient.loadCodeSystem(shortName).join();
    codeSystem.setVersions(snowstormClient.loadCodeSystemVersions(codeSystem.getShortName()).join().getItems());
    return codeSystem;
  }

  public void deactivateDescription(String branch, String descriptionId) {
    SnomedDescription description = snowstormClient.loadDescription(branch, descriptionId).join();
    SnomedConcept concept = snowstormClient.loadConcept(branch, description.getConceptId()).join();
    concept.setDescriptions(concept.getDescriptions().stream().filter(d -> !descriptionId.equals(d.getDescriptionId())).toList());
    snowstormClient.updateConcept(branch, concept).join();
  }

  public void reactivateDescription(String branch, String descriptionId) {
    SnomedDescription description = snowstormClient.loadDescription(branch, descriptionId).join();
    SnomedConcept concept = snowstormClient.loadConcept(branch, description.getConceptId()).join();
    concept.getDescriptions().stream().filter(d -> descriptionId.equals(d.getDescriptionId())).findFirst().ifPresentOrElse(d -> d.setActive(true),
        () -> concept.getDescriptions().add(description.setActive(true)));
    snowstormClient.updateConcept(branch, concept).join();
  }

  public void validateBranchName(String name) {
    if (StringUtils.isEmpty(name)) {
      throw ApiError.SN101.toApiException();
    }
    if (!BRANCH_REGEX.matcher(name).matches()) {
      throw ApiError.SN102.toApiException();
    }
  }
}
