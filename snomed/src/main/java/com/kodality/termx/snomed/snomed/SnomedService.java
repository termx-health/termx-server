package com.kodality.termx.snomed.snomed;

import com.kodality.termx.snomed.client.SnowstormClient;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termx.snomed.concept.SnomedTranslation;
import com.kodality.termx.snomed.concept.SnomedTranslationSearchParams;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.description.SnomedDescriptionSearchParams;
import com.kodality.termx.snomed.search.SnomedSearchResult;
import com.kodality.termx.snomed.snomed.translation.SnomedTranslationRepository;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class SnomedService {
  private final SnowstormClient snowstormClient;
  private final SnomedTranslationRepository translationRepository;

  private static final int MAX_COUNT = 9999;

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

  public List<SnomedDescription> searchDescriptions(SnomedDescriptionSearchParams params) {
    if (params.isAll()) {

      params.setLimit(1);
      SnomedSearchResult<SnomedDescription> result = snowstormClient.queryDescriptions(params).join();
      Integer total = result.getTotal();

      List<SnomedDescription> snomedDescriptions = new ArrayList<>();
      int bound = (total + MAX_COUNT - 1) / MAX_COUNT;
      for (int i = 0; i < bound; i++) {
        params.setLimit(MAX_COUNT);
        params.setSearchAfter(i == 0 ? null : result.getSearchAfter());
        result = snowstormClient.queryDescriptions(params).join();
        snomedDescriptions.addAll(result.getItems());
      }
      return snomedDescriptions;
    }
    return snowstormClient.queryDescriptions(params).join().getItems();
  }

  public SnomedSearchResult<SnomedDescription> searchDescriptions(String path, SnomedDescriptionSearchParams params) {
    SnomedSearchResult<SnomedDescription> descriptions = snowstormClient.queryDescriptions(path, params).join();
    String descriptionIds = descriptions.getItems().stream().map(SnomedDescription::getDescriptionId).collect(Collectors.joining(","));

    List<SnomedTranslation> translations = translationRepository.query(new SnomedTranslationSearchParams().setDescriptionIds(descriptionIds).all()).getData();
    descriptions.getItems().forEach(d -> d.setLocal(translations.stream().anyMatch(t -> t.getDescriptionId().equals(d.getDescriptionId()))));
    return descriptions;
  }
}
