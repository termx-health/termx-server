package com.kodality.termx.snomed.snomed;

import com.kodality.termx.snomed.client.SnowstormClient;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptTransactionRequest;
import com.kodality.termx.snomed.concept.SnomedConceptTransactionRequest.SnomedConceptTransactionContent;
import com.kodality.termx.snomed.concept.SnomedTranslation;
import com.kodality.termx.snomed.concept.SnomedTranslationSearchParams;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.snomed.translation.SnomedTranslationRepository;
import com.kodality.termx.core.ts.CodeSystemProvider;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class SnomedTransactionService {
  private final SnowstormClient snowstormClient;
  private final CodeSystemProvider codeSystemProvider;
  private final SnomedTranslationRepository translationRepository;

  @Transactional
  public void transaction(String path, SnomedConceptTransactionRequest request) {
    if (CollectionUtils.isEmpty(request.getConcepts())) {
      return;
    }
    request.getConcepts().forEach((key, value) -> transaction(path, key, value));
  }

  @Transactional
  public void transaction(String path, String conceptId, SnomedConceptTransactionContent content) {
    Map<String, Concept> modules = loadModules();


    SnomedConcept concept = snowstormClient.loadConcept(path + "/", conceptId).join();

    if (CollectionUtils.isNotEmpty(content.getTranslationIds())) {
      SnomedTranslationSearchParams params = new SnomedTranslationSearchParams();
      params.setIds(content.getTranslationIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
      params.setLimit(content.getTranslationIds().size());
      List<SnomedTranslation> translations = translationRepository.query(params).getData();

      concept.getDescriptions().addAll(translations.stream().map(t -> new SnomedDescription()
          .setDescriptionId(t.getDescriptionId())
          .setConceptId(t.getConceptId())
          .setModuleId(getPropertyValue(modules.get(t.getModule()), "moduleId"))
          .setLang(t.getLanguage())
          .setTerm(t.getTerm())
          .setTypeId(t.getType())
          .setStatus(t.getStatus())
      ).toList());
    }
    snowstormClient.updateConcept(path + "/", concept).join();
  }

  private String getPropertyValue(Concept concept, String property) {
    Optional<CodeSystemEntityVersion> version = concept.getLastVersion();
    return version.flatMap(v -> v.getPropertyValue(property)).map(String::valueOf).orElse(null);
  }

  private Map<String, Concept> loadModules() {
    ConceptQueryParams params = new ConceptQueryParams().setCodeSystem("snomed-module").limit(-1);
    return codeSystemProvider.searchConcepts(params).getData().stream().collect(Collectors.toMap(Concept::getCode, c -> c));
  }
}
