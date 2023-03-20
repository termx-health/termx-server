package com.kodality.termserver.snomed.snomed;

import com.kodality.termserver.snomed.client.SnowstormClient;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
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

  private static final int MAX_COUNT = 10000;

  public List<SnomedConcept> searchConcepts(SnomedConceptSearchParams params) {
    if (params.isAll()) {
      params.setLimit(1);
      Integer total = snowstormClient.queryConcepts(params).join().getTotal();
      List<SnomedConcept> snomedConcepts = new ArrayList<>();
      IntStream.range(0,(total+MAX_COUNT-1)/MAX_COUNT).forEach(i -> {
        params.setLimit((i+1)*MAX_COUNT);
        params.setOffset(i*MAX_COUNT);
        snomedConcepts.addAll(snowstormClient.queryConcepts(params).join().getItems());
      });
      return snomedConcepts;
    }
    return snowstormClient.queryConcepts(params).join().getItems();
  }
}
