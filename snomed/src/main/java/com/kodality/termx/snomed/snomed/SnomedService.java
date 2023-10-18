package com.kodality.termx.snomed.snomed;

import com.kodality.termx.snomed.client.SnowstormClient;
import com.kodality.termx.snomed.codesystem.SnomedCodeSystem;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termx.snomed.concept.SnomedTranslation;
import com.kodality.termx.snomed.concept.SnomedTranslationSearchParams;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.description.SnomedDescriptionSearchParams;
import com.kodality.termx.snomed.rf2.SnomedImportRequest;
import com.kodality.termx.snomed.search.SnomedSearchResult;
import com.kodality.termx.snomed.snomed.translation.SnomedTranslationRepository;
import jakarta.inject.Singleton;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class SnomedService {
  private final SnowstormClient snowstormClient;
  private final SnomedTranslationRepository translationRepository;

  private static final int MAX_COUNT = 9999;
  private static final int MAX_CONCEPT_COUNT = 300;

  public List<SnomedConcept> loadConcepts(List<String> ids) {
    return loadConcepts(ids, null);
  }

  public List<SnomedConcept> loadConcepts(List<String> ids, Boolean active) {
    List<SnomedConcept> concepts = new ArrayList<>();
    IntStream.range(0, (ids.size() + MAX_CONCEPT_COUNT - 1) / MAX_CONCEPT_COUNT)
        .mapToObj(i -> ids.subList(i * MAX_CONCEPT_COUNT, Math.min(ids.size(), (i + 1) * MAX_CONCEPT_COUNT))).forEach(batch -> {
          SnomedConceptSearchParams params = new SnomedConceptSearchParams();
          params.setConceptIds(batch);
          params.setLimit(batch.size());
          params.setActive(active);
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
    String jobId = snowstormClient.createImportJob(req).join();
    try {
      snowstormClient.uploadRF2File(jobId, importFile).join();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    return Map.of("jobId", jobId);
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
}
