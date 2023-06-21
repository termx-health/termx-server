package com.kodality.termserver.fhir.valueset;

import com.kodality.termserver.BinaryHttpClient;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.terminology.codesystem.CodeSystemService;
import com.kodality.termserver.terminology.codesystem.CodeSystemVersionService;
import com.kodality.termserver.terminology.codesystem.concept.ConceptService;
import com.kodality.termserver.terminology.codesystem.designation.DesignationService;
import com.kodality.termserver.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.terminology.valueset.ValueSetService;
import com.kodality.termserver.terminology.valueset.ValueSetVersionService;
import com.kodality.termserver.terminology.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemContent;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.DesignationQueryParams;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.ResourceType;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
public class ValueSetFhirImportService {
  private final ConceptService conceptService;
  private final ValueSetService valueSetService;
  private final CodeSystemService codeSystemService;
  private final DesignationService designationService;
  private final EntityPropertyService entityPropertyService;
  private final ValueSetVersionService valueSetVersionService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public ValueSet importValueSet(com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet, boolean activateVersion) {
    ValueSet valueSet = prepare(ValueSetFhirImportMapper.mapValueSet(fhirValueSet));
    ValueSetVersion valueSetVersion = prepareValueSetAndVersion(valueSet);
    if (activateVersion) {
      valueSetVersionService.activate(valueSet.getId(), valueSetVersion.getVersion());
    }
    return valueSet;
  }

  public void importValueSetFromUrl(String url) {
    String resource = getResource(url);
    importValueSet(resource);
  }

  public void importValueSet(String resource) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet fhir = FhirMapper.fromJson(resource, com.kodality.zmei.fhir.resource.terminology.ValueSet.class);
    if (!ResourceType.valueSet.equals(fhir.getResourceType())) {
      throw ApiError.TE107.toApiException();
    }
    importValueSet(fhir, false);
  }

  private String getResource(String url) {
    log.info("Loading fhir value set from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.UTF_8);
  }

  private ValueSet prepare(ValueSet valueSet) {
    if (CollectionUtils.isNotEmpty(valueSet.getVersions()) && valueSet.getVersions().get(0) != null && valueSet.getVersions().get(0).getRuleSet() != null) {
      prepareRules(valueSet);
    }
    return valueSet;
  }

  private void prepareRules(ValueSet valueSet) {
    List<ValueSetVersionRule> rules = valueSet.getVersions().get(0).getRuleSet().getRules();
    OffsetDateTime lockedDate = valueSet.getVersions().get(0).getRuleSet().getLockedDate();

    if (CollectionUtils.isEmpty(rules)) {
      return;
    }
    rules.forEach(r -> {
      prepareRuleValueSet(r);
      prepareRuleCodeSystem(r, lockedDate);
    });
  }

  private void prepareRuleValueSet(ValueSetVersionRule r) {
    if (StringUtils.isNotEmpty(r.getValueSet())) {
      valueSetVersionService.query(new ValueSetVersionQueryParams()
          .setValueSetUri(r.getValueSet())
          .setReleaseDateLe(LocalDate.now())
          .setExpirationDateGe(LocalDate.now())
      ).findFirst().ifPresent(version -> {
        r.setValueSet(version.getValueSet());
        r.setValueSetVersion(version);
      });
    }
  }

  private void prepareRuleCodeSystem(ValueSetVersionRule r, OffsetDateTime lockedDate) {
    if (StringUtils.isNotEmpty(r.getCodeSystem())) {
      codeSystemService.query(new CodeSystemQueryParams().setUri(r.getCodeSystem())).findFirst().ifPresent(cs -> r.setCodeSystem(cs.getId()));
      if (lockedDate == null) {
        r.setCodeSystemVersion(codeSystemVersionService.query(new CodeSystemVersionQueryParams()
            .setCodeSystem(r.getCodeSystem())
            .setReleaseDateLe(LocalDate.now())
            .setExpirationDateGe(LocalDate.now())).findFirst().orElse(null));
      }
    }
  }

  private List<ValueSetVersionConcept> prepareConcepts(List<ValueSetVersionConcept> concepts, String codeSystem, Long codeSystemVersionId) {
    List<ValueSetVersionConcept> conceptsToAdd = new ArrayList<>();
    if (CollectionUtils.isEmpty(concepts)) {
      return conceptsToAdd;
    }

    boolean contentNotPresent = CodeSystemContent.notPresent.equals(codeSystemService.load(codeSystem).map(CodeSystem::getContent).orElse(null));

    concepts.stream().filter(c -> c.getConcept() != null && c.getConcept().getCode() != null).forEach(concept -> {
      if (!conceptExists(concept, codeSystem, codeSystemVersionId)) {
        if (contentNotPresent) {
          conceptsToAdd.add(concept);
        } else {
          prepareConcept(concept, codeSystem);
          CodeSystemEntityVersion codeSystemEntityVersion = prepareCodeSystemVersion(concept, codeSystem, codeSystemVersionId);
          prepareDesignations(concept, codeSystemEntityVersion.getId());
        }
      }
    });

    return conceptsToAdd;
  }

  private boolean conceptExists(ValueSetVersionConcept c, String codeSystem, Long codeSystemVersionId) {
    ConceptQueryParams params = new ConceptQueryParams();
    params.setCodeSystem(codeSystem == null ? c.getConcept().getCodeSystem() : codeSystem);
    params.setCodeSystemVersionId(codeSystemVersionId);
    params.setCode(c.getConcept().getCode());
    params.setLimit(1);
    return conceptService.query(params).findFirst().isPresent();
  }

  private void prepareConcept(ValueSetVersionConcept c, String codeSystem) {
    Concept concept = conceptService.save(new Concept().setCode(c.getConcept().getCode()), codeSystem == null ? c.getConcept().getCodeSystem() : codeSystem);
    c.setConcept(concept);
  }

  private CodeSystemEntityVersion prepareCodeSystemVersion(ValueSetVersionConcept c, String codeSystem, Long codeSystemVersionId) {
    CodeSystemEntityVersion version = codeSystemEntityVersionService
        .query(
            new CodeSystemEntityVersionQueryParams()
                .setCodeSystem(c.getConcept().getCodeSystem())
                .setCode(c.getConcept().getCode())
                .setStatus(String.join(",", PublicationStatus.draft, PublicationStatus.active)))
        .findFirst()
        .orElse(
            new CodeSystemEntityVersion()
                .setCodeSystem(c.getConcept().getCodeSystem())
                .setCode(c.getConcept().getCode())
                .setStatus(PublicationStatus.draft));
    if (version.getId() == null) {
      codeSystemEntityVersionService.save(version, c.getConcept().getId());
      codeSystemEntityVersionService.activate(version.getId());
    }

    if (codeSystem != null && codeSystemVersionId != null) {
      codeSystemVersionService.linkEntityVersions(codeSystemVersionId, List.of(version.getId()));
    }
    return version;
  }

  private void prepareDesignations(ValueSetVersionConcept c, Long codeSystemEntityVersionId) {
    if (c.getConcept().getId() == null) {
      return;
    }
    Long propertyId = entityPropertyService
        .query(new EntityPropertyQueryParams().setCodeSystem(c.getConcept().getCodeSystem()).setNames("display"))
        .findFirst().map(EntityProperty::getId).orElse(null);

    if (c.getDisplay() != null) {
      designationService
          .query(new DesignationQueryParams().setConceptCode(c.getConcept().getCode()).setName(c.getDisplay().getName())
              .setDesignationKind(c.getDisplay().getDesignationKind()))
          .findFirst().ifPresentOrElse(c::setDisplay, () -> {
            c.getDisplay().setDesignationTypeId(propertyId);
            designationService.save(c.getDisplay(), codeSystemEntityVersionId, c.getConcept().getCodeSystem());
          });
    }
    if (CollectionUtils.isNotEmpty(c.getAdditionalDesignations())) {
      c.getAdditionalDesignations().forEach(d -> {
        d.setDesignationTypeId(propertyId);
        designationService.save(d, codeSystemEntityVersionId, c.getConcept().getCodeSystem());
      });
    }
  }

  private ValueSetVersion prepareValueSetAndVersion(ValueSet valueSet) {
    log.info("Checking, the value set and version exists");
    ValueSet existingValueSet = valueSetService.load(valueSet.getId());
    if (existingValueSet == null) {
      log.info("Value set {} does not exist, creating new", valueSet.getId());
      valueSetService.save(valueSet);
    }

    ValueSetVersion version = valueSet.getVersions().get(0);
    Optional<ValueSetVersion> existingVersion = valueSetVersionService.load(valueSet.getId(), version.getVersion());
    if (existingVersion.isPresent() && !existingVersion.get().getStatus().equals(PublicationStatus.draft)) {
      throw ApiError.TE104.toApiException(Map.of("version", version.getVersion()));
    }
    existingVersion.ifPresent(v -> valueSetVersionService.cancel(v.getId(), valueSet.getId()));
    log.info("Saving value set version {}", version.getVersion());
    valueSetVersionService.save(version);
    valueSetVersionRuleService.save(version.getRuleSet().getRules(), version.getRuleSet().getId(), valueSet.getId());
    return version;
  }
}
