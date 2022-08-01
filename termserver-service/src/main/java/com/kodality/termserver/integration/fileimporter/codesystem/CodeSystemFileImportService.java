package com.kodality.termserver.integration.fileimporter.codesystem;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileAnalysisRequest;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileAnalysisResponse;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingMapper;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingRequest;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingCodeSystem;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingCodeSystemVersion;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingResponse;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessor;
import com.kodality.termserver.ts.codesystem.CodeSystemService;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionService;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.ts.valueset.ValueSetService;
import com.kodality.termserver.ts.valueset.ValueSetVersionService;
import com.kodality.termserver.ts.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetVersion;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemFileImportService {
  private final ConceptService conceptService;
  private final ValueSetService valueSetService;
  private final CodeSystemService codeSystemService;
  private final EntityPropertyService entityPropertyService;
  private final ValueSetVersionService valueSetVersionService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final BinaryHttpClient client = new BinaryHttpClient();

  public FileAnalysisResponse analyze(FileAnalysisRequest request) {
    byte[] file = client.GET(request.getLink()).body();
    return analyze(request, file);
  }

  public void process(FileProcessingRequest request) {
    byte[] file = client.GET(request.getLink()).body();
    process(request, file);
  }

  public FileAnalysisResponse analyze(FileAnalysisRequest request, byte[] file) {
    FileProcessor fp = new FileProcessor();
    return fp.analyze(request.getType(), file);
  }

  public void process(FileProcessingRequest request, byte[] file) {
    FileProcessor fp = new FileProcessor();
    FileProcessingResponse result = fp.process(request.getType(), file, request.getProperties());
    saveProcessingResult(request.getCodeSystem(), request.getVersion(), request.isGenerateValueSet(), result);
  }


  @Transactional
  public void saveProcessingResult(FileProcessingCodeSystem fpCodeSystem, FileProcessingCodeSystemVersion fpVersion, boolean generateValueSet,
                                   FileProcessingResponse result) {
    FileProcessingMapper mapper = new FileProcessingMapper();

    CodeSystem existingCodeSystem = codeSystemService.load(fpCodeSystem.getId()).orElse(null);
    CodeSystem codeSystem = mapper.toCodeSystem(fpCodeSystem, existingCodeSystem);
    codeSystemService.save(codeSystem);

    CodeSystemVersion codeSystemVersion = mapper.toCodeSystemVersion(fpVersion, codeSystem.getId());
    Optional<CodeSystemVersion> existingVersion = codeSystemVersionService.load(codeSystem.getId(), codeSystemVersion.getVersion());
    existingVersion.ifPresent(version -> codeSystemVersion.setId(version.getId()));
    if (existingVersion.isPresent() && !existingVersion.get().getStatus().equals(PublicationStatus.draft)) {
      throw ApiError.TE704.toApiException();
    }
    codeSystemVersionService.save(codeSystemVersion);

    List<EntityProperty> properties = mapper.toProperties(result.getProperties());
    properties.forEach(p -> {
      Optional<EntityProperty> existingProperty = entityPropertyService.load(p.getName(), codeSystem.getId());
      if (existingProperty.isPresent()) {
        p.setId(existingProperty.get().getId());
      } else {
        entityPropertyService.save(p, codeSystem.getId());
      }
    });

    List<Concept> concepts = mapper.toConcepts(result.getEntities(), properties);
    //FIXME: this is very slow, should refactor
    for (int i = 0; i < concepts.size(); i++) {
      log.debug("Saving concept {}/{}", i + 1, concepts.size());
      Concept concept = concepts.get(i);
      conceptService.save(concept, codeSystem.getId());
      codeSystemEntityVersionService.save(concept.getVersions().get(0), concept.getId());
      codeSystemEntityVersionService.activate(concept.getVersions().get(0).getId());
      codeSystemVersionService.linkEntityVersion(codeSystemVersion.getId(), concept.getVersions().get(0).getId());
    }

    if (fpVersion.getStatus().equals(PublicationStatus.active)) {
      codeSystemVersionService.activate(codeSystem.getId(), codeSystemVersion.getVersion());
    }
    if (fpVersion.getStatus().equals(PublicationStatus.retired)) {
      codeSystemVersionService.retire(codeSystem.getId(), codeSystemVersion.getVersion());
    }

    if (generateValueSet) {
      ValueSet existingValueSet = valueSetService.load(fpCodeSystem.getId()).orElse(null);
      ValueSet valueSet = mapper.toValueSet(codeSystem, existingValueSet);
      valueSetService.save(valueSet);

      ValueSetVersion valueSetVersion = mapper.toValueSetVersion(fpVersion, valueSet.getId(), codeSystemVersion);
      Optional<ValueSetVersion> existingVSVersion = valueSetVersionService.load(valueSet.getId(), valueSetVersion.getVersion());
      existingVSVersion.ifPresent(version -> valueSetVersion.setId(version.getId()));
      if (existingVSVersion.isPresent() && !existingVSVersion.get().getStatus().equals(PublicationStatus.draft)) {
        throw ApiError.TE705.toApiException();
      }
      valueSetVersionService.save(valueSetVersion);
      valueSetVersionRuleService.save(valueSetVersion.getRuleSet().getRules(), valueSetVersion.getRuleSet().getId());
    }
  }
}
