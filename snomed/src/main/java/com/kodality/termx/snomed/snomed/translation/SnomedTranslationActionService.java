package com.kodality.termx.snomed.snomed.translation;

import com.kodality.termx.core.ts.CodeSystemProvider;
import com.kodality.termx.snomed.client.SnowstormClient;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedTranslation;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.description.SnomedDescriptionType;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SnomedTranslationActionService {
  private final SnowstormClient snowstormClient;
  private final SnomedTranslationRepository repository;
  private final CodeSystemProvider codeSystemProvider;

  public void updateStatus(Long id, String status) {
    repository.updateStatus(id, status);
  }

  public void addToBranch(Long id) {
    Map<String, Map<String, String>> modules = loadModules();

    SnomedTranslation translation = repository.load(id);
    if (translation == null || translation.getBranch() == null) {
      return;
    }
    SnomedConcept concept = snowstormClient.loadConcept(translation.getBranch() + "/", translation.getConceptId()).join();
    concept.getDescriptions().add(new SnomedDescription()
        .setDescriptionId(translation.getDescriptionId())
        .setConceptId(translation.getConceptId())
        .setModuleId(modules.get(translation.getModule()).get("moduleId"))
        .setAcceptabilityMap(Map.of(modules.get(translation.getModule()).get("refsetId"), translation.getAcceptability().toUpperCase()))
        .setLang(translation.getLanguage())
        .setTerm(translation.getTerm())
        .setTypeId(("fully-specified-name".equals(translation.getType()) ? SnomedDescriptionType.fsn : SnomedDescriptionType.synonym))
        .setStatus(translation.getStatus())
    );
    snowstormClient.updateConcept(translation.getBranch() + "/", concept).join();
  }

  private Map<String, Map<String, String>> loadModules() {
    ConceptQueryParams params = new ConceptQueryParams().setCodeSystem("snomed-module").limit(-1);
    return codeSystemProvider.searchConcepts(params).getData().stream().collect(Collectors.toMap(Concept::getCode, c -> {
      Optional<CodeSystemEntityVersion> version = c.getLastVersion();
      String moduleId = version.flatMap(v -> v.getPropertyValue("moduleId")).map(String::valueOf).orElse("");
      String refsetId = version.flatMap(v -> v.getPropertyValue("refsetId")).map(String::valueOf).orElse("");
      return Map.of("moduleId", moduleId, "refsetId", refsetId);
    }));
  }
}
