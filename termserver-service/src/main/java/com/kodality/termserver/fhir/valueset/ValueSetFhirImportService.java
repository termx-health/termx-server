package com.kodality.termserver.fhir.valueset;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.DesignationQueryParams;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.ts.codesystem.CodeSystemService;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionService;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.designation.DesignationService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.ts.valueset.ValueSetService;
import com.kodality.termserver.ts.valueset.ValueSetVersionService;
import com.kodality.termserver.ts.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termserver.ts.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetVersionConcept;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
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
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  public void importValueSets(Parameters parameters, List<String> successes, List<String> warnings) {
    List<String> urls = CollectionUtils.isNotEmpty(parameters.getParameter()) ? parameters.getParameter().stream().filter(p -> "url".equals(p.getName())).map(
        Parameter::getValueString).toList() : Collections.emptyList();
    if (urls.isEmpty()) {
      throw ApiError.TE106.toApiException();
    }
    urls.forEach(url -> {
      try {
        importValueSet(url);
        successes.add(String.format("ValueSet from resource %s imported", url));
      } catch (Exception e) {
        warnings.add(String.format("ValueSet from resource %s was not imported due to error: %s", url, e.getMessage()));
      }
    });
  }

  public void importValueSet(String url) {
    String resource = getResource(url);
    com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet =
        FhirMapper.fromJson(resource, com.kodality.zmei.fhir.resource.terminology.ValueSet.class);
    if (!ResourceType.valueSet.equals(fhirValueSet.getResourceType())) {
      throw ApiError.TE107.toApiException();
    }
    importValueSet(fhirValueSet, false);
  }

  @Transactional
  public void importValueSet(com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet, boolean activateVersion) {
    ValueSet valueSet = prepare(ValueSetFhirImportMapper.mapValueSet(fhirValueSet));
    ValueSetVersion valueSetVersion = prepareValueSetAndVersion(valueSet);
    if (activateVersion) {
      valueSetVersionService.activate(valueSet.getId(), valueSetVersion.getVersion());
    }
  }

  private String getResource(String url) {
    log.info("Loading fhir value set from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.ISO_8859_1);
  }

  private ValueSet prepare(ValueSet valueSet) {
    if (CollectionUtils.isNotEmpty(valueSet.getVersions()) && valueSet.getVersions().get(0) != null && valueSet.getVersions().get(0).getRuleSet() != null) {
      prepareRules(valueSet.getVersions().get(0).getRuleSet().getRules(), valueSet.getVersions().get(0).getRuleSet().getLockedDate());
    }
    return valueSet;
  }

  private void prepareRules(List<ValueSetVersionRule> rules, OffsetDateTime lockedDate) {
    if (CollectionUtils.isEmpty(rules)) {
      return;
    }
    rules.forEach(r -> {
      prepareRuleValueSet(r);
      prepareRuleCodeSystem(r, lockedDate);
      prepareConcepts(r.getConcepts(), r.getCodeSystem(), r.getCodeSystemVersionId());
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
        r.setValueSetVersionId(version.getId());
      });
    }
  }

  private void prepareRuleCodeSystem(ValueSetVersionRule r, OffsetDateTime lockedDate) {
    if (StringUtils.isNotEmpty(r.getCodeSystem())) {
      codeSystemService.query(new CodeSystemQueryParams().setUri(r.getCodeSystem())).findFirst().ifPresent(cs -> r.setCodeSystem(cs.getId()));
      if (lockedDate == null) {
        r.setCodeSystemVersionId(codeSystemVersionService.query(new CodeSystemVersionQueryParams()
            .setCodeSystem(r.getCodeSystem())
            .setReleaseDateLe(LocalDate.now())
            .setExpirationDateGe(LocalDate.now())).findFirst().map(CodeSystemVersion::getId).orElse(null));
      }
    }
  }

  private void prepareConcepts(List<ValueSetVersionConcept> concepts, String codeSystem, Long codeSystemVersionId) {
    if (CollectionUtils.isEmpty(concepts)) {
      return;
    }
    concepts.stream().filter(c -> c.getConcept() != null && c.getConcept().getCode() != null).forEach(concept -> {
      prepareConcept(concept, codeSystem, codeSystemVersionId);
      CodeSystemEntityVersion codeSystemEntityVersion = prepareCodeSystemVersion(concept, codeSystem, codeSystemVersionId);
      prepareDesignations(concept, codeSystemEntityVersion.getId());
    });
  }

  private void prepareConcept(ValueSetVersionConcept c, String codeSystem, Long codeSystemVersionId) {
    ConceptQueryParams params = new ConceptQueryParams();
    params.setCodeSystem(codeSystem == null ? c.getConcept().getCodeSystem() : codeSystem);
    params.setCodeSystemVersionId(codeSystemVersionId);
    params.setCode(c.getConcept().getCode());
    params.setLimit(1);
    conceptService.query(params).findFirst().ifPresentOrElse(c::setConcept, () -> {
      Concept concept = conceptService.save(new Concept().setCode(c.getConcept().getCode()), codeSystem == null ? c.getConcept().getCodeSystem() : codeSystem);
      c.setConcept(concept);
    });
  }

  private CodeSystemEntityVersion prepareCodeSystemVersion(ValueSetVersionConcept c, String codeSystem, Long codeSystemVersionId) {
    CodeSystemEntityVersion version = codeSystemEntityVersionService
        .query(
            new CodeSystemEntityVersionQueryParams()
                .setCodeSystem(c.getConcept().getCodeSystem())
                .setCode(c.getConcept().getCode())
                .setStatus(PublicationStatus.draft))
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
      codeSystemVersionService.linkEntityVersion(codeSystemVersionId, version.getId());
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
            designationService.save(c.getDisplay(), codeSystemEntityVersionId);
          });
    }
    if (CollectionUtils.isNotEmpty(c.getAdditionalDesignations())) {
      c.getAdditionalDesignations().forEach(d -> {
        d.setDesignationTypeId(propertyId);
        designationService.save(d, codeSystemEntityVersionId);
      });
    }
  }

  private ValueSetVersion prepareValueSetAndVersion(ValueSet valueSet) {
    log.info("Checking, the value set and version exists");
    Optional<ValueSet> existingValueSet = valueSetService.load(valueSet.getId());
    if (existingValueSet.isEmpty()) {
      log.info("Value set {} does not exist, creating new", valueSet.getId());
      valueSetService.save(valueSet);
    }

    ValueSetVersion version = valueSet.getVersions().get(0);
    Optional<ValueSetVersion> existingVersion = valueSetVersionService.load(valueSet.getId(), version.getVersion());
    if (existingVersion.isPresent() && !existingVersion.get().getStatus().equals(PublicationStatus.draft)) {
      throw ApiError.TE104.toApiException(Map.of("version", version.getVersion()));
    }
    log.info("Saving value set version {}", version.getVersion());
    valueSetVersionService.save(version);
    valueSetVersionRuleService.save(version.getRuleSet().getRules(), version.getRuleSet().getId());
    valueSetVersionConceptService.save(version.getConcepts(), version.getId());
    return version;
  }
}
