package com.kodality.termx.snomed.snomed.translation;

import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.snomed.concept.SnomedTranslationSearchParams;
import com.kodality.termx.snomed.concept.SnomedTranslationStatus;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.sys.lorque.LorqueProcessService;
import com.kodality.termx.sys.lorque.ProcessResult;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termx.snomed.concept.SnomedTranslation;
import com.kodality.termx.snomed.snomed.SnomedService;
import com.kodality.termx.ts.CodeSystemProvider;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Singleton
@RequiredArgsConstructor
public class SnomedRF2Service {
  private final static String process = "snomed-rf2-export";

  public static final String CONCEPT_FILE_NAME = "sct2_Concept_Snapshot_MAIN_SNOMEDCT-EST.txt";
  public static final String DESCRIPTION_FILE_NAME = "sct2_Description_Snapshot_MAIN_SNOMEDCT-EST.txt";
  public static final String RELATIONSHIP_FILE_NAME = "sct2_Relationship_Snapshot_MAIN_SNOMEDCT-EST.txt";
  public static final String REFSET_LANGUAGE_FILE_NAME = "der2_cRefset_LanguageSnapshot_MAIN_SNOMEDCT-EST.txt";

  public static final String PRIMITIVE = "900000000000074008";
  public static final String FULLY_DEFINED = "900000000000073002";

  public static final String SYNONYM = "900000000000013009";
  public static final String FULLY_SPECIFIED_NAME = "900000000000003001";
  public static final String CASE_INSENSITIVE = "900000000000448009";
  public static final String PREFERRED = "900000000000548007";
  public static final String ACCEPTABLE = "900000000000549004";


  private final SnomedService snomedService;
  private final CodeSystemProvider codeSystemProvider;
  private final LorqueProcessService lorqueProcessService;
  private final SnomedTranslationService snomedTranslationService;

  public LorqueProcess startRF2Export() {
    LorqueProcess lorqueProcess = lorqueProcessService.start(new LorqueProcess().setProcessName(SnomedRF2Service.process));
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        ProcessResult result = ProcessResult.binary(composeRF2());
        lorqueProcessService.complete(lorqueProcess.getId(), result);
      } catch (Exception e) {
        ProcessResult result = ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e));
        lorqueProcessService.fail(lorqueProcess.getId(), result);
      }
    }));

    return lorqueProcess;
  }

  private byte[] composeRF2() throws IOException {
    List<SnomedTranslation> translations = loadTranslations();
    List<SnomedConcept> concepts = loadConcepts(translations);
    Map<String, Concept> modules = loadModules();

    ImportFile conceptFile = new ImportFile().setFileName(CONCEPT_FILE_NAME).setContent(composeConceptContent(concepts));
    ImportFile descriptionFile = new ImportFile().setFileName(DESCRIPTION_FILE_NAME).setContent(composeDescriptionContent(translations, modules));
    ImportFile relationshipFile = new ImportFile().setFileName(RELATIONSHIP_FILE_NAME).setContent(composeRelationshipContent());
    ImportFile refsetLanguageFile = new ImportFile().setFileName(REFSET_LANGUAGE_FILE_NAME).setContent(composeRefsetLanguageContent(translations, modules));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ZipOutputStream zos = new ZipOutputStream(baos);
    for (ImportFile file : List.of(conceptFile, descriptionFile, relationshipFile, refsetLanguageFile)) {
      ZipEntry zipEntry = new ZipEntry(file.getFileName());
      zos.putNextEntry(zipEntry);
      zos.write(file.getContent());
      zos.closeEntry();
    }
    zos.close();
    return baos.toByteArray();
  }

  private List<SnomedTranslation> loadTranslations() {
    return snomedTranslationService.query(new SnomedTranslationSearchParams().setStatus(SnomedTranslationStatus.active).all()).getData();
  }

  private List<SnomedConcept> loadConcepts(List<SnomedTranslation> translations) {
    List<String> conceptIds = translations.stream().map(SnomedTranslation::getConceptId).collect(Collectors.toList());
    if (CollectionUtils.isEmpty(conceptIds)) {
      return List.of();
    }
    SnomedConceptSearchParams params = new SnomedConceptSearchParams();
    params.setConceptIds(conceptIds);
    params.setAll(true);
    return snomedService.searchConcepts(params);
  }

  private Map<String, Concept> loadModules() {
    ConceptQueryParams params = new ConceptQueryParams().setCodeSystem("snomed-module").limit(-1);
    return codeSystemProvider.searchConcepts(params).getData().stream().collect(Collectors.toMap(Concept::getCode, c -> c));
  }


  private byte[] composeConceptContent(List<SnomedConcept> concepts) {
    StringBuilder sb = new StringBuilder();
    sb.append("id\teffectiveTime\tactive\tmoduleId\tdefinitionStatusId\t\n");
    concepts.forEach(concept -> {
      sb.append(concept.getConceptId()).append("\t");
      sb.append("\t");
      sb.append((concept.isActive() ? "1" : "0")).append("\t");
      sb.append(concept.getConceptId()).append("\t");
      sb.append(("FULLY_DEFINED".equals(concept.getDefinitionStatus()) ? FULLY_DEFINED : PRIMITIVE)).append("\t").append("\n");
    });
    return sb.toString().getBytes();
  }

  private byte[] composeDescriptionContent(List<SnomedTranslation> translations, Map<String, Concept> modules) {
    StringBuilder sb = new StringBuilder();
    sb.append("id\teffectiveTime\tactive\tmoduleId\trefsetId\tconceptId\tlanguageCode\ttypeId\tterm\tcaseSignificanceId\t\n");
    translations.forEach(translation -> {
      sb.append(translation.getDescriptionId()).append("\t");
      sb.append("\t");
      sb.append("1").append("\t");
      sb.append(getPropertyValue(modules.get(translation.getModule()), "moduleId")).append("\t");
      sb.append(getPropertyValue(modules.get(translation.getModule()), "refsetId")).append("\t");
      sb.append(translation.getConceptId()).append("\t");
      sb.append(translation.getLanguage()).append("\t");
      sb.append(("fully-specified-name".equals(translation.getType()) ? FULLY_SPECIFIED_NAME : SYNONYM)).append("\t");
      sb.append(translation.getTerm()).append("\t");
      sb.append(CASE_INSENSITIVE).append("\t").append("\n");
    });
    return sb.toString().getBytes();
  }

  private byte[] composeRelationshipContent() {
    return "id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\t\n".getBytes();
  }

  private byte[] composeRefsetLanguageContent(List<SnomedTranslation> translations, Map<String, Concept> modules) {
    StringBuilder sb = new StringBuilder();
    sb.append("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tacceptabilityId\t\n");
    translations.forEach(translation -> {
      sb.append(UUID.randomUUID()).append("\t");
      sb.append("\t");
      sb.append("1").append("\t");
      sb.append(getPropertyValue(modules.get(translation.getModule()), "moduleId")).append("\t");
      sb.append(getPropertyValue(modules.get(translation.getModule()), "refsetId")).append("\t");
      sb.append(translation.getDescriptionId()).append("\t");
      sb.append("preferred".equals(translation.getAcceptability()) ? PREFERRED : ACCEPTABLE).append("\t").append("\n");
    });
    return sb.toString().getBytes();
  }

  private String getPropertyValue(Concept concept, String property) {
    Optional<CodeSystemEntityVersion> version = concept.getLastVersion();
    return version.flatMap(v -> v.getPropertyValue(property)).map(String::valueOf).orElse(null);
  }


  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ImportFile {
    private byte[] content;
    private String fileName;
  }
}
