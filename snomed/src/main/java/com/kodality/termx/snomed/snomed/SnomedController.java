package com.kodality.termx.snomed.snomed;


import com.kodality.commons.util.AsyncHelper;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.snomed.Privilege;
import com.kodality.termx.snomed.branch.SnomedBranch;
import com.kodality.termx.snomed.branch.SnomedBranchRequest;
import com.kodality.termx.snomed.client.SnowstormClient;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termx.snomed.concept.SnomedConceptTransactionRequest;
import com.kodality.termx.snomed.concept.SnomedTranslation;
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
import com.kodality.termx.snomed.snomed.translation.SnomedRF2Service;
import com.kodality.termx.snomed.snomed.translation.SnomedTranslationService;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.sys.lorque.LorqueProcessService;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.reactivex.Flowable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

@Slf4j
@Controller("/snomed")
@RequiredArgsConstructor
public class SnomedController {
  private final SnomedService snomedService;
  private final SnowstormClient snowstormClient;
  private final SnomedRF2Service snomedRF2Service;
  private final LorqueProcessService lorqueProcessService;
  private final SnomedTransactionService transactionService;
  private final SnomedTranslationService translationService;

  //----------------Branches----------------

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/branches")
  public List<SnomedBranch> loadBranches() {
    return snowstormClient.loadBranches().join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/branches/{path}")
  public SnomedBranch loadBranch(@Parameter String path) {
    return snowstormClient.loadBranch(parsePath(path)).join();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/branches")
  public SnomedBranch createBranch(@Body SnomedBranchRequest request) {
    return snowstormClient.createBranch(request).join();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Put("/branches/{path}")
  public SnomedBranch updateBranch(@Parameter String path, @Body SnomedBranchRequest request) {
    return snowstormClient.updateBranch(parsePath(path), request).join();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Delete("/branches/{path}")
  public HttpResponse<?> deleteBranch(@Parameter String path) {
    snowstormClient.deleteBranch(parsePath(path)).join();
    return HttpResponse.ok();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/branches/{path}/lock")
  public HttpResponse<?> lockBranch(@PathVariable String path, @QueryValue String lockMessage) {
    snowstormClient.lockBranch(parsePath(path), Map.of("lockMessage", lockMessage)).join();
    return HttpResponse.ok();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/branches/{path}/unlock")
  public HttpResponse<?> unlockBranch(@Parameter String path) {
    snowstormClient.unlockBranch(parsePath(path)).join();
    return HttpResponse.ok();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/branches/{path}/integrity-check")
  public Object branchIntegrityCheck(@Parameter String path) {
    return snowstormClient.branchIntegrityCheck(parsePath(path)).join();
  }


  //----------------RF2----------------

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/exports")
  public Map<String, String> createExportJob(@Body SnomedExportRequest request) {
    return Map.of("jobId", snowstormClient.createExportJob(request).join());
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/exports/{jobId}")
  public SnomedExportJob loadExportJob(@Parameter String jobId) {
    return snowstormClient.loadExportJob(jobId).join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get(value = "/exports/{jobId}/archive", produces = "application/zip")
  public HttpResponse<?> getRF2File(@Parameter String jobId) {
    MutableHttpResponse<byte[]> response = HttpResponse.ok(snowstormClient.getRF2File(jobId));
    return response
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=snomed_translations.zip")
        .contentType(MediaType.of("application/zip"));
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA)
  public Map<String, String> createImportJob(Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    SnomedImportRequest req = JsonUtil.fromJson(request.getValue(), SnomedImportRequest.class);
    byte[] importFile = readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet());
    return snomedService.importRF2File(req, importFile);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/imports/{jobId}")
  public SnomedImportJob loadImportJob(@Parameter String jobId) {
    return snowstormClient.loadImportJob(jobId).join();
  }

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

  @Authorized(Privilege.SNOMED_VIEW)
  @Post("/branches/{path}/concepts/transaction")
  public HttpResponse<?> conceptTransaction(@Parameter String path, @Body SnomedConceptTransactionRequest request) {
    transactionService.transaction(parsePath(path), request);
    return HttpResponse.ok();
  }

  //----------------Descriptions----------------

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/descriptions{?params*}")
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

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/branches/{path}/descriptions{?params*}")
  public SnomedSearchResult<SnomedDescription> findDescriptions(@Parameter String path, SnomedDescriptionSearchParams params) {
    path += "/";
    return snomedService.searchDescriptions(parsePath(path), params);
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Delete("/branches/{path}/descriptions/{descriptionId}")
  public HttpResponse<?> deleteDescription(@Parameter String path, @Parameter String descriptionId) {
    path += "/";
    snowstormClient.deleteDescription(parsePath(path), descriptionId).join();
    return HttpResponse.ok();
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
  @Get("/translations")
  public List<SnomedTranslation> queryTranslations(@QueryValue Boolean active, @QueryValue Boolean unlinked) {
    return translationService.loadAll(active, unlinked);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/translations/{id}")
  public SnomedTranslation loadTranslation(@Parameter Long id) {
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

  private String parsePath(String path) {
    return path.replace("--", "/");
  }

  private static byte[] readBytes(CompletedFileUpload file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}