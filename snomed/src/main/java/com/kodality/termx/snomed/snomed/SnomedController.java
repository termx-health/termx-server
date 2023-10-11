package com.kodality.termx.snomed.snomed;


import com.kodality.commons.util.AsyncHelper;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.snomed.Privilege;
import com.kodality.termx.snomed.branch.SnomedBranch;
import com.kodality.termx.snomed.branch.SnomedBranchRequest;
import com.kodality.termx.snomed.client.SnowstormClient;
import com.kodality.termx.snomed.codesystem.SnomedCodeSystem;
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
import com.kodality.termx.snomed.snomed.csv.SnomedConceptCsvService;
import com.kodality.termx.snomed.snomed.rf2.SnomedRF2Service;
import com.kodality.termx.snomed.snomed.translation.SnomedTranslationService;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.sys.lorque.LorqueProcessService;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.ProvenanceService;
import com.kodality.termx.sys.provenance.ProvenanceUtil;
import com.kodality.termx.utils.FileUtil;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.util.CollectionUtils;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
  private final SnomedConceptCsvService snomedConceptCsvService;
  private final LorqueProcessService lorqueProcessService;
  private final SnomedTransactionService transactionService;
  private final SnomedTranslationService translationService;
  private final ProvenanceService provenanceService;


  //----------------CodeSystems----------------

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/codesystems")
  public List<SnomedCodeSystem> loadCodeSystems() {
    return snowstormClient.loadCodeSystems().join().getItems();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/codesystems")
  public HttpResponse<?> createCodeSystem(@Body SnomedCodeSystem codeSystem) {
    snowstormClient.createCodeSystem(codeSystem).join();
    return HttpResponse.ok();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Delete("/codesystems/{shortName}")
  public HttpResponse<?> deleteCodeSystem(@PathVariable String shortName) {
    snowstormClient.deleteCodeSystem(shortName).join();
    return HttpResponse.ok();
  }

  //----------------Branches----------------

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/branches")
  public List<SnomedBranch> loadBranches() {
    return snowstormClient.loadBranches().join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/branches/{path}")
  public SnomedBranch loadBranch(@PathVariable String path) {
    return snowstormClient.loadBranch(parsePath(path)).join();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/branches")
  public SnomedBranch createBranch(@Body SnomedBranchRequest request) {
    return snowstormClient.createBranch(request).join();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Put("/branches/{path}")
  public SnomedBranch updateBranch(@PathVariable String path, @Body SnomedBranchRequest request) {
    return snowstormClient.updateBranch(parsePath(path), request).join();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Delete("/branches/{path}")
  public HttpResponse<?> deleteBranch(@PathVariable String path) {
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
  public HttpResponse<?> unlockBranch(@PathVariable String path) {
    snowstormClient.unlockBranch(parsePath(path)).join();
    return HttpResponse.ok();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/branches/{path}/integrity-check")
  public Object branchIntegrityCheck(@PathVariable String path) {
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
  public SnomedExportJob loadExportJob(@PathVariable String jobId) {
    return snowstormClient.loadExportJob(jobId).join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get(value = "/exports/{jobId}/archive", produces = "application/zip")
  public HttpResponse<?> getRF2File(@PathVariable String jobId) {
    MutableHttpResponse<byte[]> response = HttpResponse.ok(snowstormClient.getRF2File(jobId));
    return response
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=snomed_translations.zip")
        .contentType(MediaType.of("application/zip"));
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA)
  public Map<String, String> createImportJob(Publisher<CompletedFileUpload> file, @Part("request") MemoryAttribute request) {
    SnomedImportRequest req = JsonUtil.fromJson(request.getValue(), SnomedImportRequest.class);
    byte[] importFile = FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet());
    return snomedService.importRF2File(req, importFile);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/imports/{jobId}")
  public SnomedImportJob loadImportJob(@PathVariable String jobId) {
    return snowstormClient.loadImportJob(jobId).join();
  }

  //----------------Concepts----------------

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/concepts/{conceptId}")
  public SnomedConcept loadConcept(@PathVariable String conceptId) {
    return snowstormClient.loadConcept(conceptId).join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/concepts/{conceptId}/children")
  public List<SnomedConcept> findConceptChildren(@PathVariable String conceptId) {
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

    Map<String, List<SnomedDescription>> descriptions = getDescriptions(concepts.getItems().stream().map(SnomedConcept::getConceptId).toList());
    concepts.getItems().forEach(c -> c.setDescriptions(descriptions.get(c.getConceptId())));

    return concepts;
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Post(value = "/concepts/export-csv")
  public HttpResponse<?> startConceptCsvExport(@Body SnomedConceptSearchParams params) {
    LorqueProcess lorqueProcess = snomedConceptCsvService.startCsvExport(params);
    return HttpResponse.accepted().body(lorqueProcess);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get(value = "/concepts/export-csv/result/{lorqueProcessId}", produces = "application/csv")
  public HttpResponse<?> getConceptCsv(Long lorqueProcessId) {
    MutableHttpResponse<byte[]> response = HttpResponse.ok(lorqueProcessService.load(lorqueProcessId).getResult());
    return response
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=concepts.csv")
        .contentType(MediaType.of("application/csv"));
  }


  @Authorized(Privilege.SNOMED_VIEW)
  @Post("/branches/{path}/concepts/transaction")
  public HttpResponse<?> conceptTransaction(@PathVariable String path, @Body SnomedConceptTransactionRequest request) {
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
    response.getItems().forEach(item -> futures.add(
        snowstormClient.loadConcept(item.getConcept().getConceptId()).thenApply(c -> item.getConcept().setDescriptions(c.getDescriptions()))));
    futures.joinAll();

    return response;
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/branches/{path}/descriptions{?params*}")
  public SnomedSearchResult<SnomedDescription> findDescriptions(@PathVariable String path, SnomedDescriptionSearchParams params) {
    path += "/";
    return snomedService.searchDescriptions(parsePath(path), params);
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Delete("/branches/{path}/descriptions/{descriptionId}")
  public HttpResponse<?> deleteDescription(@PathVariable String path, @PathVariable String descriptionId) {
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
  public SnomedTranslation loadTranslation(@PathVariable Long id) {
    return translationService.load(id);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/concepts/{conceptId}/translations")
  public List<SnomedTranslation> loadTranslations(@PathVariable String conceptId) {
    return translationService.load(conceptId);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Post("/concepts/{conceptId}/translations")
  public HttpResponse<?> saveTranslations(@PathVariable String conceptId, @Body List<SnomedTranslation> translations) {
    provenanceTranslations("snomed-translations-save", conceptId, () -> {
      translationService.save(conceptId, translations);
    });
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

  private void provenanceTranslations(String action, String conceptId, Runnable save) {
    List<SnomedTranslation> before = translationService.load(conceptId);
    save.run();
    List<SnomedTranslation> after = translationService.load(conceptId);
    provenanceService.create(new Provenance(action, "CodeSystem", "snomed-ct").setChanges(
        ProvenanceUtil.diff(Map.of("snomed." + conceptId, before), Map.of("snomed." + conceptId, after))
    ));
  }

  private String parsePath(String path) {
    return path.replace("--", "/");
  }

  private Map<String, List<SnomedDescription>> getDescriptions(List<String> conceptIds) {
    if (CollectionUtils.isEmpty(conceptIds)) {
      return Map.of();
    }
    SnomedDescriptionSearchParams descriptionParams = new SnomedDescriptionSearchParams();
    descriptionParams.setConceptIds(conceptIds);
    descriptionParams.setAll(true);
    return snomedService.searchDescriptions(descriptionParams).stream().collect(Collectors.groupingBy(SnomedDescription::getConceptId));
  }
}
