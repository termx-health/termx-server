package com.kodality.termserver.ts.codesystem;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetAssociationQueryParams;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.ts.mapset.association.MapSetAssociationService;
import com.kodality.termserver.ts.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termserver.valueset.ValueSetVersionRuleQueryParams;
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
public class CodeSystemDeleteService {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final EntityPropertyService entityPropertyService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final MapSetAssociationService mapSetAssociationService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Transactional
  public void deleteCodeSystem(String codeSystem) {
    checkCodeSystemUsed(codeSystem);

    deleteCodeSystemConcepts(codeSystem);
    deleteCodeSystemVersions(codeSystem);
    deleteCodeSystemProperties(codeSystem);

    codeSystemService.cancel(codeSystem);
  }

  private void checkCodeSystemUsed(String codeSystem) {
    List<String> requiredCodeSystems = List.of("codesystem-content-mode", "concept-property-type", "contact-point-system", "contact-point-use",
        "filter-operator", "namingsystem-identifier-type", "namingsystem-type", "publication-status", "snomed-ct", "v3-ietf3066");
    if (requiredCodeSystems.contains(codeSystem)) {
      throw ApiError.TE204.toApiException();
    }

    CodeSystemQueryParams codeSystemParams = new CodeSystemQueryParams().setBaseCodeSystem(codeSystem);
    codeSystemParams.setLimit(1);
    Optional<CodeSystem> childCodeSystem = codeSystemService.query(codeSystemParams).findFirst();
    if (childCodeSystem.isPresent()) {
      throw ApiError.TE205.toApiException(Map.of("codeSystem", childCodeSystem.get().getId()));
    }

    ValueSetVersionRuleQueryParams valueSetVersionRuleParams = new ValueSetVersionRuleQueryParams().setCodeSystem(codeSystem);
    valueSetVersionRuleParams.setLimit(0);
    if (valueSetVersionRuleService.query(valueSetVersionRuleParams).getMeta().getTotal() > 0) {
      throw ApiError.TE206.toApiException();
    }
  }

  private void deleteCodeSystemConcepts(String codeSystem) {
    ConceptQueryParams conceptParams = new ConceptQueryParams().setCodeSystem(codeSystem);
    conceptParams.all();
    List<Concept> concepts = conceptService.query(conceptParams).getData().stream().filter(c -> c.getCodeSystem().equals(codeSystem)).collect(Collectors.toList());

    checkConceptsUsed(concepts, codeSystem);
    concepts.forEach(c -> {
      c.getVersions().forEach(v -> codeSystemEntityVersionService.cancel(v.getId(), codeSystem));
      conceptService.cancel(c.getId(), codeSystem);
    });
  }

  private void checkConceptsUsed(List<Concept> concepts, String codeSystem) {
    if (CollectionUtils.isEmpty(concepts)) {
      return;
    }

    MapSetAssociationQueryParams mapSetAssociationParams = new MapSetAssociationQueryParams();
    mapSetAssociationParams.setSourceCode(concepts.stream().map(Concept::getCode).collect(Collectors.joining(",")));
    mapSetAssociationParams.setSourceSystem(codeSystem);
    mapSetAssociationParams.setLimit(1);
    Optional<MapSetAssociation> association = mapSetAssociationService.query(mapSetAssociationParams).findFirst();
    if (association.isPresent()) {
      throw ApiError.TE208.toApiException(Map.of("mapSet", association.get().getMapSet()));
    }
    mapSetAssociationParams.setTargetCode(mapSetAssociationParams.getSourceCode());
    mapSetAssociationParams.setTargetSystem(mapSetAssociationParams.getSourceSystem());
    mapSetAssociationParams.setSourceCode(null);
    mapSetAssociationParams.setSourceSystem(null);
    association = mapSetAssociationService.query(mapSetAssociationParams).findFirst();
    if (association.isPresent()) {
      throw ApiError.TE208.toApiException(Map.of("mapSet", association.get().getMapSet()));
    }
  }

  private void deleteCodeSystemVersions(String codeSystem) {
    CodeSystemVersionQueryParams codeSystemVersionParams = new CodeSystemVersionQueryParams().setCodeSystem(codeSystem);
    codeSystemVersionParams.all();
    List<CodeSystemVersion> codeSystemVersions = codeSystemVersionService.query(codeSystemVersionParams).getData();

    checkCodeSystemVersionsUsed(codeSystemVersions);
    codeSystemVersions.forEach(v -> codeSystemVersionService.cancel(v.getId(), codeSystem));
  }

  private void checkCodeSystemVersionsUsed(List<CodeSystemVersion> codeSystemVersions) {
    if (CollectionUtils.isEmpty(codeSystemVersions)) {
      return;
    }
    ValueSetVersionRuleQueryParams valueSetVersionRuleParams = new ValueSetVersionRuleQueryParams();
    valueSetVersionRuleParams.setCodeSystemVersionIds(codeSystemVersions.stream().map(v -> String.valueOf(v.getId())).collect(Collectors.joining(",")));
    valueSetVersionRuleParams.setLimit(0);
    if (valueSetVersionRuleService.query(valueSetVersionRuleParams).getMeta().getTotal() > 0) {
      throw ApiError.TE207.toApiException();
    }
  }

  private void deleteCodeSystemProperties(String codeSystem) {
    EntityPropertyQueryParams entityPropertyParams = new EntityPropertyQueryParams();
    entityPropertyParams.setCodeSystem(codeSystem);
    entityPropertyParams.all();

    entityPropertyService.query(entityPropertyParams).getData().forEach(p -> {
      entityPropertyService.cancel(p.getId(), codeSystem);
    });
  }
}
