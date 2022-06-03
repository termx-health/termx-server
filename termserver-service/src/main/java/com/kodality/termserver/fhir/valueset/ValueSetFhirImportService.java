package com.kodality.termserver.fhir.valueset;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
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
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetRuleSet.ValueSetRule;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersion.ValueSetConcept;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  public void importValueSets(Parameters parameters, List<String> warnings) {
    List<String> urls = CollectionUtils.isNotEmpty(parameters.getParameter()) ? parameters.getParameter().stream().filter(p -> "url".equals(p.getName())).map(
        Parameter::getValueString).toList() : Collections.emptyList();
    if (urls.isEmpty()) {
      throw ApiError.TE110.toApiException();
    }
    urls.forEach(url -> {
      try {
        importValueSet(url);
      } catch (Exception e) {
        warnings.add(String.format("ValueSet from resource {%s} was not imported due to error: {%s}", url, e.getMessage()));
      }
    });
  }

  public void importValueSet(String url) {
    String resource = getResource(url);
    com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet =
        FhirMapper.fromJson(resource, com.kodality.zmei.fhir.resource.terminology.ValueSet.class);
    importValueSet(fhirValueSet);
  }

  @Transactional
  public void importValueSet(com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet) {
    ValueSet valueSet = prepare(ValueSetFhirImportMapper.mapValueSet(fhirValueSet));
    ValueSetVersion version = prepareValueSetAndVersion(valueSet);
    valueSetVersionService.saveConcepts(version.getId(), findConcepts(fhirValueSet));
  }

  private String getResource(String url) {
    log.info("Loading fhir value set from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.ISO_8859_1);
  }

  private ValueSet prepare(ValueSet valueSet) {
    if (CollectionUtils.isNotEmpty(valueSet.getVersions()) && valueSet.getVersions().get(0) != null && valueSet.getVersions().get(0).getRuleSet() != null) {
      prepareRules(valueSet.getVersions().get(0).getRuleSet().getExcludeRules(), valueSet.getVersions().get(0).getRuleSet().getLockedDate());
      prepareRules(valueSet.getVersions().get(0).getRuleSet().getIncludeRules(), valueSet.getVersions().get(0).getRuleSet().getLockedDate());
    }
    return valueSet;
  }

  private void prepareRules(List<ValueSetRule> rules, OffsetDateTime lockedDate) {
    if (CollectionUtils.isEmpty(rules)) {
      return;
    }
    rules.forEach(r -> {
      prepareRuleValueSet(r);
      prepareRuleCodeSystem(r, lockedDate);
      prepareConcepts(r);
    });
  }

  private void prepareRuleValueSet(ValueSetRule r) {
    if (StringUtils.isNotEmpty(r.getValueSet())) {
      valueSetVersionService.query(new ValueSetVersionQueryParams()
          .setValueSetUri(r.getValueSet())
          .setReleaseDateLe(LocalDate.now())
          .setExpirationDateGe(LocalDate.now())
      ).findFirst().ifPresent(version -> {
        r.setValueSet(version.getValueSet());
        r.setValueSetVersion(version.getVersion());
      });
    }
  }

  private void prepareRuleCodeSystem(ValueSetRule r, OffsetDateTime lockedDate) {
    if (StringUtils.isNotEmpty(r.getCodeSystem())) {
      codeSystemService.query(new CodeSystemQueryParams().setUri(r.getCodeSystem())).findFirst().ifPresent(cs -> r.setCodeSystem(cs.getId()));
      if (lockedDate == null) {
        r.setCodeSystemVersion(codeSystemVersionService.query(new CodeSystemVersionQueryParams()
            .setCodeSystem(r.getCodeSystem())
            .setReleaseDateLe(LocalDate.now())
            .setExpirationDateGe(LocalDate.now())).findFirst().map(CodeSystemVersion::getVersion).orElse(null));
      }
    }
  }

  private void prepareConcepts(ValueSetRule r) {
    if (CollectionUtils.isEmpty(r.getConcepts())) {
      return;
    }
    r.getConcepts().forEach(c -> {
      if (c.getConcept() != null && c.getConcept().getCode() != null) {
        conceptService.get(r.getCodeSystem(), r.getCodeSystemVersion(), c.getConcept().getCode()).ifPresentOrElse(c::setConcept, () -> {
          Concept concept = conceptService.save(new Concept().setCode(c.getConcept().getCode()), r.getCodeSystem());
          c.setConcept(concept);
        });
      }
      prepareDesignations(c);
    });
  }

  private void prepareDesignations(ValueSetConcept c) {
    if (c.getConcept() == null || c.getConcept().getId() == null) {
      return;
    }
    Long propertyId = entityPropertyService
        .query(new EntityPropertyQueryParams().setCodeSystem(c.getConcept().getCodeSystem()).setNames("display"))
        .findFirst().map(EntityProperty::getId).orElse(null);

    CodeSystemEntityVersion codeSystemEntityVersion = new CodeSystemEntityVersion()
        .setCodeSystem(c.getConcept().getCodeSystem())
        .setCode(c.getConcept().getCode())
        .setStatus(PublicationStatus.draft);

    if (c.getDisplay() != null) {
      designationService
          .query(new DesignationQueryParams().setConceptCode(c.getConcept().getCode()).setName(c.getDisplay().getName()).setDesignationKind(c.getDisplay().getDesignationKind()))
          .findFirst().ifPresentOrElse(c::setDisplay, () -> {
            codeSystemEntityVersionService.save(codeSystemEntityVersion, c.getConcept().getId());
            c.getDisplay().setDesignationTypeId(propertyId);
            designationService.save(c.getDisplay(), codeSystemEntityVersion.getId());
          });
    }
    if (CollectionUtils.isNotEmpty(c.getAdditionalDesignations())) {
      if (codeSystemEntityVersion.getId() == null) {
        codeSystemEntityVersionService.save(codeSystemEntityVersion, c.getConcept().getId());
      }
      c.getAdditionalDesignations().forEach(d -> {
        d.setDesignationTypeId(propertyId);
        designationService.save(d, codeSystemEntityVersion.getId());
      });
    }
  }

  private ValueSetVersion prepareValueSetAndVersion(ValueSet valueSet) {
    log.info("Checking, the value set and version exists");
    Optional<ValueSet> existingValueSet = valueSetService.get(valueSet.getId());
    if (existingValueSet.isEmpty()) {
      log.info("Value set {} does not exist, creating new", valueSet.getId());
      valueSetService.create(valueSet);
    }

    ValueSetVersion version = valueSet.getVersions().get(0);
    Optional<ValueSetVersion> existingVersion = valueSetVersionService.getVersion(valueSet.getId(), version.getVersion());
    if (existingVersion.isPresent() && existingVersion.get().getStatus().equals(PublicationStatus.active)) {
      throw ApiError.TE105.toApiException(Map.of("version", version.getVersion()));
    }
    log.info("Saving value set version {}", version.getVersion());
    valueSetVersionService.save(version);
    return version;
  }

  private List<Concept> findConcepts(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    if (valueSet.getCompose() == null) {
      return new ArrayList<>();
    }
    List<Concept> concepts = new ArrayList<>();
    valueSet.getCompose().getInclude().forEach(i -> {
      ConceptQueryParams params = new ConceptQueryParams();
      params.setCodeSystemUri(i.getSystem());
      params.setCodeSystemVersion(i.getVersion());
      params.setCode(i.getConcept() == null ? null : i.getConcept().stream().map(c -> c.getCode()).collect(Collectors.joining(",")));
      concepts.addAll(conceptService.query(params).getData());
    });
    return concepts;
  }
}
