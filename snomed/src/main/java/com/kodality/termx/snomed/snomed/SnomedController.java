package com.kodality.termx.snomed.snomed;


import com.kodality.commons.util.AsyncHelper;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.snomed.Privilege;
import com.kodality.termx.snomed.branch.SnomedAuthoringStatsResponse;
import com.kodality.termx.snomed.branch.SnomedBranch;
import com.kodality.termx.snomed.branch.SnomedBranchRequest;
import com.kodality.termx.snomed.client.SnowstormClient;
import com.kodality.termx.snomed.codesystem.SnomedCodeSystem;
import com.kodality.termx.snomed.codesystem.SnomedCodeSystem.SnomedCodeSystemVersion;
import com.kodality.termx.snomed.codesystem.SnomedCodeSystemUpgradeRequest;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
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
import com.kodality.termx.snomed.snomed.translation.SnomedTranslationActionService;
import com.kodality.termx.snomed.snomed.translation.SnomedTranslationProvenanceService;
import com.kodality.termx.snomed.snomed.translation.SnomedTranslationService;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.core.sys.lorque.LorqueProcessService;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.core.sys.provenance.ProvenanceUtil;
import com.kodality.termx.core.utils.FileUtil;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
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
import io.reactivex.rxjava3.core.Flowable;
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
  private final SnomedTranslationService translationService;
  private final SnomedTranslationActionService translationActionService;
  private final SnomedTranslationProvenanceService provenanceService;


  //----------------CodeSystems----------------

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/codesystems")
  public List<SnomedCodeSystem> loadCodeSystems() {
    return snomedService.loadCodeSystems();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Get("/codesystems/{shortName}")
  public SnomedCodeSystem loadCodeSystem(@PathVariable String shortName) {
    return snomedService.loadCodeSystem(shortName);
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/codesystems")
  public HttpResponse<?> createCodeSystem(@Body SnomedCodeSystem codeSystem) {
    snowstormClient.createCodeSystem(codeSystem).join();
    return HttpResponse.ok();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/codesystems/{shortName}/upgrade")
  public HttpResponse<?> upgradeCodeSystem(@PathVariable String shortName, @Body SnomedCodeSystemUpgradeRequest request) {
    snowstormClient.upgradeCodeSystem(shortName, request).join();
    return HttpResponse.ok();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Put("/codesystems/{shortName}")
  public HttpResponse<?> createCodeSystem(@PathVariable String shortName, @Body SnomedCodeSystem codeSystem) {
    snowstormClient.updateCodeSystem(shortName, codeSystem).join();
    return HttpResponse.ok();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Delete("/codesystems/{shortName}")
  public HttpResponse<?> deleteCodeSystem(@PathVariable String shortName) {
    snowstormClient.deleteCodeSystem(shortName).join();
    return HttpResponse.ok();
  }


  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/codesystems/{shortName}/versions")
  public HttpResponse<?> createCodeSystemVersion(@PathVariable String shortName, @Body SnomedCodeSystemVersion version) {
    snowstormClient.createCodeSystemVersion(shortName, version).join();
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
    snomedService.validateBranchName(request.getName());
    return snowstormClient.createBranch(request).join();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Put("/branches/{path}")
  public SnomedBranch updateBranch(@PathVariable String path, @Body SnomedBranchRequest request) {
    snomedService.validateBranchName(request.getName());
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

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/{path}/authoring-stats/changed-fully-specified-names")
  public List<SnomedAuthoringStatsResponse> getChangedFsn(@PathVariable String path) {
    return snowstormClient.getChangedFsn(parsePath(path)).join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/{path}/authoring-stats/new-descriptions")
  public List<SnomedAuthoringStatsResponse> getNewDescriptions(@PathVariable String path) {
    return snowstormClient.getNewDescriptions(parsePath(path)).join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/{path}/authoring-stats/new-synonyms-on-existing-concepts")
  public List<SnomedAuthoringStatsResponse> getNewSynonyms(@PathVariable String path) {
    return snowstormClient.getNewSynonyms(parsePath(path)).join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/{path}/authoring-stats/inactivated-synonyms")
  public List<SnomedAuthoringStatsResponse> getInactivatedSynonyms(@PathVariable String path) {
    return snowstormClient.getInactivatedSynonyms(parsePath(path)).join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/{path}/authoring-stats/reactivated-synonyms")
  public List<SnomedAuthoringStatsResponse> getReactivatedSynonyms(@PathVariable String path) {
    return snowstormClient.getReactivatedSynonyms(parsePath(path)).join();
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
  public Map<String, String> createImportJob(Publisher<CompletedFileUpload> file, @Part("request") String request) {
    SnomedImportRequest req = JsonUtil.fromJson(request, SnomedImportRequest.class);
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
  public SnomedConcept loadConcept(@PathVariable String conceptId, @QueryValue @Nullable String branch) {
    if (StringUtils.isNotEmpty(branch)) {
      return snowstormClient.loadConcept(branch + "/", conceptId).join();
    }
    return snowstormClient.loadConcept(conceptId).join();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/concepts/{conceptId}/children")
  public List<SnomedConcept> findConceptChildren(@PathVariable String conceptId, @QueryValue @Nullable String branch) {
    if (StringUtils.isNotEmpty(branch)) {
      List<SnomedConcept> concepts = snowstormClient.findConceptChildren(branch + "/", conceptId).join();
      AsyncHelper futures = new AsyncHelper();
      concepts.forEach(concept -> futures.add(snowstormClient.loadConcept(branch + "/", concept.getConceptId()).thenApply(c -> concept.setDescriptions(c.getDescriptions()))));
      futures.joinAll();
      return concepts;
    }
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

    Map<String, List<SnomedDescription>> descriptions = getDescriptions(concepts.getItems().stream().map(SnomedConcept::getConceptId).toList(), params.getBranch());
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

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/branches/{path}/descriptions/{descriptionId}/deactivate")
  public HttpResponse<?> deactivateDescription(@PathVariable String path, @PathVariable String descriptionId) {
    path += "/";
    snomedService.deactivateDescription(parsePath(path), descriptionId);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.SNOMED_EDIT)
  @Post("/branches/{path}/descriptions/{descriptionId}/reactivate")
  public HttpResponse<?> reactivateDescription(@PathVariable String path, @PathVariable String descriptionId) {
    path += "/";
    snomedService.reactivateDescription(parsePath(path), descriptionId);
    return HttpResponse.ok();
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
  public List<SnomedTranslation> queryTranslations(@QueryValue @Nullable Boolean active,
                                                   @QueryValue @Nullable Boolean unlinked,
                                                   @QueryValue @Nullable String branch) {
    return translationService.loadAll(active, unlinked, branch);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/translations/{id}")
  public SnomedTranslation loadTranslation(@PathVariable Long id) {
    return translationService.load(id);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Post("/translations/{id}/add-to-branch")
  public HttpResponse<?> addTranslationToBranch(@PathVariable Long id) {
    translationActionService.addToBranch(id);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Get("/concepts/{conceptId}/translations")
  public List<SnomedTranslation> loadTranslations(@PathVariable String conceptId) {
    return translationService.load(conceptId);
  }

  @Authorized(Privilege.SNOMED_VIEW)
  @Post("/concepts/{conceptId}/translations")
  public HttpResponse<?> saveTranslations(@PathVariable String conceptId, @Body List<SnomedTranslation> translations) {
    provenanceService.provenanceTranslations("snomed-translations-save", conceptId, () -> translationService.save(conceptId, translations));
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

  //----------------Provenances----------------

  @Authorized(Privilege.SNOMED_VIEW)
  @Get(uri = "/concepts/{conceptId}/provenances")
  public List<Provenance> queryProvenances(@PathVariable String conceptId) {
    return provenanceService.find(conceptId);
  }

  private String parsePath(String path) {
    return path.replace("--", "/");
  }

  private Map<String, List<SnomedDescription>> getDescriptions(List<String> conceptIds, String branch) {
    if (CollectionUtils.isEmpty(conceptIds)) {
      return Map.of();
    }
    SnomedDescriptionSearchParams descriptionParams = new SnomedDescriptionSearchParams();
    descriptionParams.setConceptIds(conceptIds);
    descriptionParams.setAll(true);
    return snomedService.searchDescriptions(branch, descriptionParams).stream().collect(Collectors.groupingBy(SnomedDescription::getConceptId));
  }
}
