package com.kodality.termserver.integration.snomed;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.AsyncHelper;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.client.SnowstormClient;
import com.kodality.termserver.common.ImportLogger;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.termserver.snomed.SnomedImportRequest;
import com.kodality.termserver.snomed.SnomedSearchResult;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.snomed.decriptionitem.SnomedDescriptionItemResponse;
import com.kodality.termserver.snomed.decriptionitem.SnomedDescriptionItemSearchParams;
import com.kodality.termserver.snomed.refset.SnomedRefsetMemberResponse;
import com.kodality.termserver.snomed.refset.SnomedRefsetResponse;
import com.kodality.termserver.snomed.refset.SnomedRefsetSearchParams;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/snomed")
@RequiredArgsConstructor
public class SnomedController {
  private final SnowstormClient snowstormClient;
  private final SnomedService snomedService;
  private final ImportLogger importLogger;

  //----------------Concepts----------------

  @Authorized("*.Snomed.view")
  @Get("/concepts/{conceptId}")
  public SnomedConcept loadConcept(@Parameter String conceptId) {
    return snowstormClient.loadConcept(conceptId).join();
  }

  @Authorized("*.Snomed.view")
  @Get("/concepts/{conceptId}/children")
  public List<SnomedConcept> findConceptChildren(@Parameter String conceptId) {
    List<SnomedConcept> concepts = snowstormClient.findConceptChildren(conceptId).join();
    AsyncHelper futures = new AsyncHelper();
    concepts.forEach(concept -> futures.add(snowstormClient.loadConcept(concept.getConceptId()).thenApply(c -> concept.setDescriptions(c.getDescriptions()))));
    futures.joinAll();
    return concepts;
  }

  @Authorized("*.Snomed.view")
  @Get("/concepts{?params*}")
  public SnomedSearchResult<SnomedConcept> findConcepts(SnomedConceptSearchParams params) {
    SnomedSearchResult<SnomedConcept> concepts = snowstormClient.queryConcepts(params).join();

    AsyncHelper futures = new AsyncHelper();
    concepts.getItems().forEach(concept -> futures.add(snowstormClient.loadConcept(concept.getConceptId()).thenApply(c -> concept.setDescriptions(c.getDescriptions()))));
    futures.joinAll();

    return concepts;
  }

  //----------------Descriptions----------------

  @Authorized("*.Snomed.view")
  @Get("/concept-descriptions{?params*}")
  public SnomedDescriptionItemResponse findConceptDescriptions(SnomedDescriptionItemSearchParams params) {
    SnomedDescriptionItemResponse response = snowstormClient.findConceptDescriptions(params).join();

    AsyncHelper futures = new AsyncHelper();
    response.getItems().forEach(item -> futures.add(snowstormClient.loadConcept(item.getConcept().getConceptId()).thenApply(c -> item.getConcept().setDescriptions(c.getDescriptions()))));
    futures.joinAll();

    return response;
  }

  //----------------RefSets----------------

  @Authorized("*.Snomed.view")
  @Get("/refsets{?params*}")
  public SnomedRefsetResponse findRefsets(SnomedRefsetSearchParams params) {
    return snowstormClient.findRefsets(params).join();
  }

  @Authorized("*.Snomed.view")
  @Get("/refset-members{?params*}")
  public SnomedRefsetMemberResponse findRefsetMembers(SnomedRefsetSearchParams params) {
    return snowstormClient.findRefsetMembers(params).join();
  }

  //----------------Import----------------

  @Authorized("*.CodeSystem.edit")
  @Post("/import")
  public JobLogResponse importConcepts(@Body SnomedImportRequest request) {
    JobLogResponse jobLogResponse = importLogger.createJob("snomed-ct", "import");
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("SNOMED CT concepts import started");
        long start = System.currentTimeMillis();
        snomedService.importConcepts(request);
        log.info("SNOMED CT concepts import took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(jobLogResponse.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while importing SNOMED CT concepts", e);
        importLogger.logImport(jobLogResponse.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while importing SNOMED CT concepts", e);
        importLogger.logImport(jobLogResponse.getJobId(), ApiError.TE700.toApiException());
      }
    }));
    return jobLogResponse;
  }

}
