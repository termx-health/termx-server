package com.kodality.termx.terminology.fileimporter.valueset;

import com.kodality.commons.db.transaction.TransactionManager;
import com.kodality.commons.model.Issue;
import com.kodality.commons.model.Severity;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.fhir.FhirFshConverter;
import com.kodality.termx.terminology.fhir.valueset.ValueSetFhirImportService;
import com.kodality.termx.terminology.fileimporter.valueset.utils.ValueSetFileImportProcessor;
import com.kodality.termx.terminology.fileimporter.valueset.utils.ValueSetFileImportRequest;
import com.kodality.termx.terminology.fileimporter.valueset.utils.ValueSetFileImportRequest.FileProcessingValueSet;
import com.kodality.termx.terminology.fileimporter.valueset.utils.ValueSetFileImportRequest.FileProcessingValueSetVersion;
import com.kodality.termx.terminology.fileimporter.valueset.utils.ValueSetFileImportResponse;
import com.kodality.termx.core.http.BinaryHttpClient;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetImportService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemContent;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetImportAction;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import io.micronaut.core.util.CollectionUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.transaction.annotation.Transactional;

import static com.kodality.termx.terminology.fileimporter.valueset.utils.ValueSetFileImportMapper.toValueSet;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ValueSetFileImportService {
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;
  private final ValueSetFhirImportService valueSetFhirImportService;
  private final ValueSetImportService valueSetImportService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ConceptService conceptService;
  private final Optional<FhirFshConverter> fhirFshConverter;

  private final BinaryHttpClient client = new BinaryHttpClient();

  public ValueSetFileImportResponse process(ValueSetFileImportRequest request) {
    byte[] file = loadFile(request.getLink());
    return process(request, file);
  }

  public ValueSetFileImportResponse process(ValueSetFileImportRequest request, byte[] file) {
    if ("json".equals(request.getType())) {
      valueSetFhirImportService.importValueSet(new String(file, StandardCharsets.UTF_8), request.getValueSet().getId());
      return new ValueSetFileImportResponse();
    } else if ("fsh".equals(request.getType())) {
      String json = fhirFshConverter.orElseThrow(ApiError.TE806::toApiException).toFhir(new String(file, StandardCharsets.UTF_8)).join();
      valueSetFhirImportService.importValueSet(json, request.getValueSet().getId());
      return new ValueSetFileImportResponse();
    }

    return save(request, file);
  }


  @Transactional
  public ValueSetFileImportResponse save(ValueSetFileImportRequest request, byte[] file) {
    ValueSetFileImportResponse resp = new ValueSetFileImportResponse();

    FileProcessingValueSet reqValueSet = request.getValueSet();
    FileProcessingValueSetVersion reqVersion = request.getVersion();

    log.info("Trying to load existing ValueSet");
    ValueSet existingValueSet = valueSetService.load(reqValueSet.getId(), true).orElse(null);
    log.info("Trying to load existing ValueSetVersion");
    ValueSetVersion existingValueSetVersion = findVersion(reqValueSet.getId(), reqVersion.getNumber()).orElse(null);
    log.info("Trying to load existing ValueSetRule");
    ValueSetVersionRule existingRule = valueSetVersionRuleService.load(reqVersion.getRule().getId()).orElse(null);

    prepare(request);

    List<ValueSetVersionConcept> concepts = file == null ? null : ValueSetFileImportProcessor.process(request, existingRule, file);

    // Mapping
    log.info("Mapping to ValueSet");
    var mappedValueSet = toValueSet(reqValueSet, reqVersion, concepts, existingValueSet, existingValueSetVersion, existingRule);

    // Validation
    resp.getErrors().addAll(validate(request, existingRule, concepts));

    if (request.isDryRun()) {
      // Version comparison
      log.info("Calculating the diff");
      log.info("\tCreating VS copy");
      ValueSet copy = JsonUtil.fromJson(JsonUtil.toJson(mappedValueSet), ValueSet.class);
      log.info("\tAdding _shadow suffix to version");
      copy.getVersions().forEach(cv -> cv.setId(null).setVersion(cv.getVersion() + "_shadow"));

      try {
        log.info("\tImporting VS copy");
        ValueSetImportAction action = new ValueSetImportAction()
            .setActivate(PublicationStatus.active.equals(request.getVersion().getStatus()))
            .setRetire(PublicationStatus.retired.equals(request.getVersion().getStatus()))
            .setCleanRun(request.isCleanVersion())
            .setSpaceToAdd(request.getSpace() != null && request.getSpacePackage() != null ?
                String.join("|", request.getSpace(), request.getSpacePackage()) : null);
        valueSetImportService.importValueSet(copy, action);
      } catch (Exception e) {
        TransactionManager.rollback();
        resp.getErrors().add(ApiError.TE725.toIssue(Map.of("exception", ExceptionUtils.getMessage(e))));
        return resp;
      }

      log.info("\tCancelling the _shadow versions");
      copy.getVersions().forEach(vsv -> valueSetVersionService.cancel(vsv.getId()));

      TransactionManager.rollback();
      return resp;
    }

    // Actual import

    // NB: 'importValueSet' cancels the persisted version and saves a new one.
    // If the new version has an ID, the importer will simply update the cancelled one.
    // Setting null to prevent that.
    mappedValueSet.getVersions().forEach(cv -> cv.setId(null));
    ValueSetImportAction action = new ValueSetImportAction()
        .setActivate(PublicationStatus.active.equals(request.getVersion().getStatus()))
        .setRetire(PublicationStatus.retired.equals(request.getVersion().getStatus()))
        .setCleanRun(request.isCleanVersion())
        .setSpaceToAdd(request.getSpace() != null && request.getSpacePackage() != null ?
            String.join("|", request.getSpace(), request.getSpacePackage()) : null);
    valueSetImportService.importValueSet(mappedValueSet, action);
    return resp;
  }

  private void prepare(ValueSetFileImportRequest request) {
    if (request.getVersion().getRule() != null && request.getVersion().getRule().getCodeSystemUri() != null) {
      CodeSystemVersion csv = codeSystemVersionService.loadLastVersionByUri(request.getVersion().getRule().getCodeSystemUri());
      if (csv == null) {
        throw ApiError.TE727.toApiException(Map.of("uri", request.getVersion().getRule().getCodeSystemUri()));
      }
      request.getVersion().getRule().setCodeSystem(csv.getCodeSystem());
      request.getVersion().getRule().setCodeSystemVersionId(csv.getId());
    }
  }

  private List<Issue> validate(ValueSetFileImportRequest request, ValueSetVersionRule existingRule, List<ValueSetVersionConcept> concepts) {
    List<Issue> issues = new ArrayList<>();
    log.info("Validating concepts");
    if (CollectionUtils.isEmpty(concepts)) {
      return issues;
    }

    String csId = request.getVersion() != null && request.getVersion().getRule() != null && request.getVersion().getRule().getCodeSystem() != null ?
        request.getVersion().getRule().getCodeSystem() : existingRule != null ? existingRule.getCodeSystem() : null;
    CodeSystem codeSystem = codeSystemService.load(csId).orElse(null);
    if (codeSystem == null) {
      issues.add(new Issue(Severity.ERROR, String.format("CodeSystem %s not found", csId)));
      return issues;
    }
    if (CodeSystemContent.notPresent.equals(codeSystem.getContent())) {
      return issues;
    }
    List<String> conceptCodes = concepts.stream().map(c -> c.getConcept().getCode()).distinct().toList();
    List<String> csConceptCodes = conceptService.query(
        new ConceptQueryParams().setCodeSystem(codeSystem.getId()).setCode(String.join(",", conceptCodes)).limit(conceptCodes.size()))
        .getData().stream().map(Concept::getCode).toList();
    conceptCodes.forEach(code -> {
      if (!csConceptCodes.contains(code)) {
        issues.add(new Issue(Severity.ERROR, String.format("Concept %s is not present in %s CodeSystem", code, codeSystem.getId())));
      }
    });
    return issues;
  }

  private Optional<ValueSetVersion> findVersion(String vsId, String version) {
    return valueSetVersionService.load(vsId, version);
  }


  private byte[] loadFile(String link) {
    try {
      return client.GET(link).body();
    } catch (Exception e) {
      throw ApiError.TE711.toApiException();
    }
  }

}
