package com.kodality.termserver.integration.snomed;

import com.kodality.commons.util.AsyncHelper;
import com.kodality.termserver.client.SnowstormClient;
import com.kodality.termserver.snomed.SnomedSearchResult;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.snomed.decriptionitem.SnomedDescriptionItemResponse;
import com.kodality.termserver.snomed.decriptionitem.SnomedDescriptionItemSearchParams;
import com.kodality.termserver.snomed.refset.SnomedRefsetMemberResponse;
import com.kodality.termserver.snomed.refset.SnomedRefsetResponse;
import com.kodality.termserver.snomed.refset.SnomedRefsetSearchParams;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/snomed")
@RequiredArgsConstructor
public class SnomedController {
  private final SnowstormClient snowstormClient;

  //----------------Concepts----------------

  @Get("/concepts/{conceptId}")
  public SnomedConcept loadConcept(@Parameter String conceptId) {
    return snowstormClient.loadConcept(conceptId).join();
  }

  @Get("/concepts/{conceptId}/children")
  public List<SnomedConcept> findConceptChildren(@Parameter String conceptId) {
    List<SnomedConcept> concepts = snowstormClient.findConceptChildren(conceptId).join();
    AsyncHelper futures = new AsyncHelper();
    concepts.forEach(concept -> futures.add(snowstormClient.loadConcept(concept.getConceptId()).thenApply(c -> concept.setDescriptions(c.getDescriptions()))));
    futures.joinAll();
    return concepts;
  }

  @Get("/concepts{?params*}")
  public SnomedSearchResult<SnomedConcept> findConcepts(SnomedConceptSearchParams params) {
    SnomedSearchResult<SnomedConcept> concepts = snowstormClient.queryConcepts(params).join();

    AsyncHelper futures = new AsyncHelper();
    concepts.getItems().forEach(concept -> futures.add(snowstormClient.loadConcept(concept.getConceptId()).thenApply(c -> concept.setDescriptions(c.getDescriptions()))));
    futures.joinAll();

    return concepts;
  }

  //----------------Descriptions----------------

  @Get("/concept-descriptions{?params*}")
  public SnomedDescriptionItemResponse findConceptDescriptions(SnomedDescriptionItemSearchParams params) {
    SnomedDescriptionItemResponse response = snowstormClient.findConceptDescriptions(params).join();

    AsyncHelper futures = new AsyncHelper();
    response.getItems().forEach(item -> futures.add(snowstormClient.loadConcept(item.getConcept().getConceptId()).thenApply(c -> item.getConcept().setDescriptions(c.getDescriptions()))));
    futures.joinAll();

    return response;
  }
  //----------------RefSets----------------

  @Get("/refsets{?params*}")
  public SnomedRefsetResponse findRefsets(SnomedRefsetSearchParams params) {
    return snowstormClient.findRefsets(params).join();
  }

  @Get("/refset-members{?params*}")
  public SnomedRefsetMemberResponse findRefsetMembers(SnomedRefsetSearchParams params) {
    return snowstormClient.findRefsetMembers(params).join();
  }

}
