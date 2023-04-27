package com.kodality.termserver.snomed.snomed;

import com.kodality.termserver.snomed.client.SnowstormClient;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.snomed.search.SnomedSearchResult;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class SnomedService {
  private final SnowstormClient snowstormClient;

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
}
