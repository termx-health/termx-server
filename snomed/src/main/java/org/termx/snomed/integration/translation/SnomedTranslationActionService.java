package org.termx.snomed.integration.translation;

import org.termx.core.sys.provenance.Provenance;
import org.termx.core.sys.provenance.ProvenanceService;
import org.termx.core.sys.provenance.ProvenanceUtil;
import org.termx.core.ts.CodeSystemProvider;
import org.termx.snomed.client.SnowstormClient;
import org.termx.snomed.concept.SnomedConcept;
import org.termx.snomed.concept.SnomedTranslation;
import org.termx.snomed.description.SnomedDescription;
import org.termx.snomed.description.SnomedDescriptionType;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SnomedTranslationActionService {
  private final SnowstormClient snowstormClient;
  private final SnomedTranslationRepository repository;
  private final CodeSystemProvider codeSystemProvider;
  private final ProvenanceService provenanceService;


  public void updateStatus(Long id, String status) {
    SnomedTranslation before = repository.load(id);
    repository.updateStatus(id, status);
    SnomedTranslation after = repository.load(id);

    Provenance provenance = new Provenance("status-change", "Concept", before.getConceptId()).addContext("part-of", "CodeSystem", "snomed-ct");
    provenance.setChanges(ProvenanceUtil.diff(
        Map.of("description." + before.getDescriptionId(), before),
        Map.of("description." + after.getDescriptionId(), after)));
    provenanceService.create(provenance);
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
