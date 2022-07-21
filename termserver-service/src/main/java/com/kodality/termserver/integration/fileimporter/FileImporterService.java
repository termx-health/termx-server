package com.kodality.termserver.integration.fileimporter;

import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.integration.fileimporter.utils.FileAnalysisRequest;
import com.kodality.termserver.integration.fileimporter.utils.FileAnalysisResponse;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingMapper;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingRequest;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingRequest.FileProcessingCodeSystem;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingRequest.FileProcessingCodeSystemVersion;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessor;
import com.kodality.termserver.ts.codesystem.CodeSystemService;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionService;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class FileImporterService {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final EntityPropertyService entityPropertyService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final BinaryHttpClient client = new BinaryHttpClient();

  public FileAnalysisResponse analyze(FileAnalysisRequest request) {
    byte[] file = client.GET(request.getLink()).body();

    FileProcessor fp = new FileProcessor();
    return fp.analyze(request.getType(), file);
  }

  public void process(FileProcessingRequest request) {
    byte[] file = client.GET(request.getLink()).body();
    FileProcessor fp = new FileProcessor();
    FileProcessingResponse result = fp.process(request.getType(), file, request.getProperties());
    saveProcessingResult(request.getCodeSystem(), request.getVersion(), result);
  }

  @Transactional
  public void saveProcessingResult(FileProcessingCodeSystem fpCodeSystem, FileProcessingCodeSystemVersion fpVersion, FileProcessingResponse result) {
    FileProcessingMapper mapper = new FileProcessingMapper();

    CodeSystem existingCodeSystem = codeSystemService.load(fpCodeSystem.getId()).orElse(null);
    CodeSystem codeSystem = mapper.toCodeSystem(fpCodeSystem, existingCodeSystem);
    codeSystemService.save(codeSystem);

    CodeSystemVersion codeSystemVersion = mapper.toCodeSystemVersion(fpVersion, codeSystem.getId());
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
    concepts.forEach(concept -> {
      conceptService.save(concept, codeSystem.getId());
      codeSystemEntityVersionService.save(concept.getVersions().get(0), concept.getId());
      codeSystemEntityVersionService.activate(concept.getVersions().get(0).getId());
      codeSystemVersionService.linkEntityVersion(codeSystemVersion.getId(), concept.getVersions().get(0).getId());
    });

    if (fpVersion.getStatus().equals(PublicationStatus.active)) {
      codeSystemVersionService.activate(codeSystem.getId(), codeSystemVersion.getVersion());
    }
    if (fpVersion.getStatus().equals(PublicationStatus.retired)) {
      codeSystemVersionService.retire(codeSystem.getId(), codeSystemVersion.getVersion());
    }
  }
}
