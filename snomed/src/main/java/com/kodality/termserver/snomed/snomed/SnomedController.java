package com.kodality.termserver.snomed.snomed;


import com.kodality.commons.util.AsyncHelper;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.snomed.Privilege;
import com.kodality.termserver.sys.lorque.LorqueProcess;
import com.kodality.termserver.sys.lorque.LorqueProcessService;
import com.kodality.termserver.snomed.client.SnowstormClient;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.snomed.concept.SnomedTranslation;
import com.kodality.termserver.snomed.decriptionitem.SnomedDescriptionItemResponse;
import com.kodality.termserver.snomed.decriptionitem.SnomedDescriptionItemSearchParams;
import com.kodality.termserver.snomed.refset.SnomedRefsetMemberResponse;
import com.kodality.termserver.snomed.refset.SnomedRefsetResponse;
import com.kodality.termserver.snomed.refset.SnomedRefsetSearchParams;
import com.kodality.termserver.snomed.search.SnomedSearchResult;
import com.kodality.termserver.snomed.snomed.translation.SnomedRF2Service;
import com.kodality.termserver.snomed.snomed.translation.SnomedTranslationService;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/snomed")
@RequiredArgsConstructor
public class SnomedController {
  private final SnowstormClient snowstormClient;
  private final SnomedRF2Service snomedRF2Service;
  private final LorqueProcessService lorqueProcessService;
  private final SnomedTranslationService translationService;

  //----------------Concepts----------------

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/concepts/{conceptId}")
  public SnomedConcept loadConcept(@Parameter String conceptId) {
    return snowstormClient.loadConcept(conceptId).join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/concepts/{conceptId}/children")
  public List<SnomedConcept> findConceptChildren(@Parameter String conceptId) {
    List<SnomedConcept> concepts = snowstormClient.findConceptChildren(conceptId).join();
    AsyncHelper futures = new AsyncHelper();
    concepts.forEach(concept -> futures.add(snowstormClient.loadConcept(concept.getConceptId()).thenApply(c -> concept.setDescriptions(c.getDescriptions()))));
    futures.joinAll();
    return concepts;
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/concepts{?params*}")
  public SnomedSearchResult<SnomedConcept> findConcepts(SnomedConceptSearchParams params) {
    SnomedSearchResult<SnomedConcept> concepts = snowstormClient.queryConcepts(params).join();

    AsyncHelper futures = new AsyncHelper();
    concepts.getItems().forEach(concept -> futures.add(snowstormClient.loadConcept(concept.getConceptId()).thenApply(c -> concept.setDescriptions(c.getDescriptions()))));
    futures.joinAll();

    return concepts;
  }

  //----------------Descriptions----------------

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/concept-descriptions{?params*}")
  public SnomedDescriptionItemResponse findConceptDescriptions(SnomedDescriptionItemSearchParams params) {
    params.setActive(true);
    params.setConceptActive(true);
    params.setGroupByConcept(true);

    SnomedDescriptionItemResponse response = snowstormClient.findConceptDescriptions(params).join();

    AsyncHelper futures = new AsyncHelper();
    response.getItems().forEach(item -> futures.add(snowstormClient.loadConcept(item.getConcept().getConceptId()).thenApply(c -> item.getConcept().setDescriptions(c.getDescriptions()))));
    futures.joinAll();

    return response;
  }

  //----------------RefSets----------------

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/refsets{?params*}")
  public SnomedRefsetResponse findRefsets(SnomedRefsetSearchParams params) {
    return snowstormClient.findRefsets(params).join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/refset-members{?params*}")
  public SnomedRefsetMemberResponse findRefsetMembers(SnomedRefsetSearchParams params) {
    return snowstormClient.findRefsetMembers(params).join();
  }


//----------------Translations----------------

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/translations/{id}")
  public SnomedTranslation load(@Parameter Long id) {
    return translationService.load(id);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/concepts/{conceptId}/translations")
  public List<SnomedTranslation> loadTranslations(@Parameter String conceptId) {
    return translationService.load(conceptId);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Post("/concepts/{conceptId}/translations")
  public HttpResponse<?> saveTranslations(@Parameter String conceptId, @Body List<SnomedTranslation> translations) {
    translationService.save(conceptId, translations);
    return HttpResponse.ok();
  }
  @Authorized(Privilege.SNOMED_VIEW)
  @Post(value = "/translations/export-rf2")
  public HttpResponse<?> startRF2Export() {
    LorqueProcess lorqueProcess = snomedRF2Service.startRF2Export();
    return HttpResponse.accepted().body(lorqueProcess);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get(value = "/translations/export-rf2/result/{lorqueProcessId}", produces = "application/zip")
  public HttpResponse<?> getRF2(Long lorqueProcessId) {
    MutableHttpResponse<byte[]> response = HttpResponse.ok(lorqueProcessService.load(lorqueProcessId).getResult());
    return response
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=snomed_translations.zip")
        .contentType(MediaType.of("application/zip"));
  }
}
