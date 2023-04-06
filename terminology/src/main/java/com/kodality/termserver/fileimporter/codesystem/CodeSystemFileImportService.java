package com.kodality.termserver.fileimporter.codesystem;

import com.kodality.termserver.BinaryHttpClient;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termserver.fileimporter.codesystem.utils.FileAnalysisRequest;
import com.kodality.termserver.fileimporter.codesystem.utils.FileAnalysisResponse;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingMapper;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingCodeSystem;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingCodeSystemVersion;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingResponse;
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessor;
import com.kodality.termserver.terminology.codesystem.CodeSystemImportService;
import com.kodality.termserver.terminology.codesystem.CodeSystemService;
import com.kodality.termserver.terminology.codesystem.CodeSystemVersionService;
import com.kodality.termserver.terminology.valueset.ValueSetService;
import com.kodality.termserver.terminology.valueset.ValueSetVersionService;
import com.kodality.termserver.terminology.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemFileImportService {
  private final ValueSetService valueSetService;
  private final CodeSystemService codeSystemService;
  private final ValueSetVersionService valueSetVersionService;
  private final CodeSystemImportService codeSystemImportService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;
  private final CodeSystemFhirImportService codeSystemFhirImportService;

  private final BinaryHttpClient client = new BinaryHttpClient();

  public FileAnalysisResponse analyze(FileAnalysisRequest request) {
    byte[] file = loadFile(request.getLink());
    return analyze(request, file);
  }

  public void process(FileProcessingRequest request) {
    byte[] file = loadFile(request.getLink());
    process(request, file);
  }

  private byte[] loadFile(String link) {
    try {
      return client.GET(link).body();
    } catch (Exception e) {
      throw ApiError.TE711.toApiException();
    }
  }

  public FileAnalysisResponse analyze(FileAnalysisRequest request, byte[] file) {
    FileProcessor fp = new FileProcessor();
    return fp.analyze(request.getType(), file);
  }

  public void process(FileProcessingRequest request, byte[] file) {
    FileProcessor fp = new FileProcessor();
    if ("json".equals(request.getType())) {
      codeSystemFhirImportService.importCodeSystem(new String(file, StandardCharsets.UTF_8));
      return;
    }
    FileProcessingResponse result = fp.process(request.getType(), file, request.getProperties());
    saveProcessingResult(request.getCodeSystem(), request.getVersion(), request.isGenerateValueSet(), result);
  }


  @Transactional
  public void saveProcessingResult(FileProcessingCodeSystem fpCodeSystem, FileProcessingCodeSystemVersion fpVersion, boolean generateValueSet,
                                   FileProcessingResponse result) {
    FileProcessingMapper mapper = new FileProcessingMapper();

    CodeSystem existingCodeSystem = codeSystemService.load(fpCodeSystem.getId()).orElse(null);
    CodeSystem codeSystem = mapper.toCodeSystem(fpCodeSystem, fpVersion, result, existingCodeSystem);
    List<AssociationType> associationTypes = mapper.toAssociationTypes(result.getProperties());
    codeSystemImportService.importCodeSystem(codeSystem, associationTypes, fpVersion.getStatus().equals(PublicationStatus.active));

    if (fpVersion.getStatus().equals(PublicationStatus.retired)) {
      codeSystemVersionService.retire(codeSystem.getId(), codeSystem.getVersions().get(0).getVersion());
    }

    if (generateValueSet) {
      ValueSet existingValueSet = valueSetService.load(fpCodeSystem.getId()).orElse(null);
      ValueSet valueSet = mapper.toValueSet(codeSystem, existingValueSet);
      valueSetService.save(valueSet);

      ValueSetVersion valueSetVersion = mapper.toValueSetVersion(fpVersion, valueSet.getId(), codeSystem.getVersions().get(0));
      Optional<ValueSetVersion> existingVSVersion = valueSetVersionService.load(valueSet.getId(), valueSetVersion.getVersion());
      existingVSVersion.ifPresent(version -> valueSetVersion.setId(version.getId()));
      if (existingVSVersion.isPresent() && !existingVSVersion.get().getStatus().equals(PublicationStatus.draft)) {
        throw ApiError.TE104.toApiException(Map.of("version", codeSystem.getVersions().get(0).getVersion()));
      }
      valueSetVersionService.save(valueSetVersion);
      valueSetVersionRuleService.save(valueSetVersion.getRuleSet().getRules(), valueSetVersion.getRuleSet().getId(), valueSet.getId());
    }
  }
}
