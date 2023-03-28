package com.kodality.termserver.fhir.valueset;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.terminology.valueset.ValueSetService;
import com.kodality.termserver.terminology.valueset.ValueSetVersionService;
import com.kodality.termserver.terminology.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionConceptQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.OperationOutcome.OperationOutcomeIssue;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ValueSetFhirService {
  private final ValueSetFhirMapper mapper;
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final ValueSetFhirImportService fhirImportService;

  public com.kodality.zmei.fhir.resource.terminology.ValueSet get(String valueSetId, Map<String, List<String>> params) {
    String versionCode = Optional.ofNullable(params.getOrDefault("version", null)).map(v -> String.join(",", v)).orElse(null);

    ValueSetQueryParams valueSetParams = new ValueSetQueryParams();
    valueSetParams.setId(valueSetId);
    valueSetParams.setVersionVersion(versionCode);
    valueSetParams.setLimit(1);
    ValueSet valueSet = valueSetService.query(valueSetParams).findFirst().orElse(null);
    if (valueSet == null) {
      return null;
    }
    ValueSetVersion version = StringUtils.isEmpty(versionCode) ? valueSetVersionService.loadLastVersion(valueSetId) : valueSetVersionService.load(valueSetId, versionCode).orElse(null);
    return mapper.toFhir(valueSet, version);
  }

  public Bundle search(Map<String, List<String>> params) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    ValueSetQueryParams queryParams = new ValueSetQueryParams();
    queryParams.setVersionVersion(fhirParams.getFirst("version").orElse(null));
    queryParams.setUri(fhirParams.getFirst("url").orElse(null));
    queryParams.setNameContains(fhirParams.getFirst("title").orElse(fhirParams.getFirst("name").orElse(null)));
    queryParams.setVersionStatus(fhirParams.getFirst("status").orElse(null));
    queryParams.setCodeSystemUri(fhirParams.getFirst("reference").orElse(null));
    queryParams.setVersionSource(fhirParams.getFirst("publisher").orElse(null));
    queryParams.setDescriptionContains(fhirParams.getFirst("description").orElse(null));
    queryParams.setConceptCode(fhirParams.getFirst("code").orElse(null));
    queryParams.setLimit(fhirParams.getCount());
    queryParams.setDecorated(true);
    List<ValueSet> valueSets = valueSetService.query(queryParams).getData();
    return Bundle.of("searchset", valueSets.stream().filter(vs -> vs.getVersions() != null)
        .flatMap(vs -> vs.getVersions().stream().map(vsv -> mapper.toFhir(vs, vsv))).collect(Collectors.toList()));
  }

  public com.kodality.zmei.fhir.resource.terminology.ValueSet expand(Map<String, List<String>> params, OperationOutcome outcome) {
    outcome.setIssue(new ArrayList<>());
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    if (fhirParams.getFirst("url").isEmpty()) {
      outcome.getIssue().add(new OperationOutcomeIssue()
          .setSeverity("error").setCode("required").setDetails(new CodeableConcept().setText(String.format("Parameter '%s' not provided", "url"))));
      return null;
    }
    return expand(null, params, outcome);
  }

  public com.kodality.zmei.fhir.resource.terminology.ValueSet expand(String valuesetId, Map<String, List<String>> params, OperationOutcome outcome) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);

    ValueSetQueryParams vsParams = new ValueSetQueryParams();
    vsParams.setUri(fhirParams.getFirst("url").orElse(null));
    vsParams.setId(valuesetId);
    vsParams.setLimit(1);
    ValueSet valueSet = valueSetService.query(vsParams).findFirst().orElse(null);
    if (valueSet == null) {
      outcome.getIssue().add(new OperationOutcomeIssue().setSeverity("error").setCode("not-found").setDetails(new CodeableConcept().setText("Value set not found")));
      return null;
    }

    ValueSetVersion version;
    if (fhirParams.getFirst("valueSetVersion").isPresent()) {
      ValueSetVersionQueryParams vsvParams = new ValueSetVersionQueryParams();
      vsvParams.setValueSetUri(fhirParams.getFirst("url").get());
      vsvParams.setVersion(fhirParams.getFirst("valueSetVersion").get());
      vsvParams.setDecorated(true);
      vsvParams.setLimit(1);
      version = valueSetVersionService.query(vsvParams).findFirst().orElse(null);
    } else {
      version = valueSetVersionService.loadLastVersion(valueSet.getId());
    }

    if (version == null) {
      outcome.getIssue().add(new OperationOutcomeIssue()
          .setSeverity("error").setCode("not-found").setDetails(new CodeableConcept().setText("Value set version not found")));
      return null;
    }
    List<ValueSetVersionConcept> expandedConcepts = valueSetVersionConceptService.expand(valueSet.getId(), version.getVersion(), null);
    return mapper.toFhir(valueSet, version, expandedConcepts);
  }

  public Parameters validateCode(Map<String, List<String>> params, OperationOutcome outcome) {
    outcome.setIssue(new ArrayList<>());
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    if (fhirParams.getFirst("url").isEmpty() || fhirParams.getFirst("valueSetVersion").isEmpty() || fhirParams.getFirst("code").isEmpty()) {
      outcome.getIssue().addAll(Stream.of("url", "valueSetVersion", "code").filter(key -> fhirParams.getFirst(key).isEmpty())
          .map(key -> new OperationOutcomeIssue().setSeverity("error").setCode("required")
              .setDetails(new CodeableConcept().setText(String.format("Parameter '%s' not provided", key)))).toList());
      return null;
    }

    Optional<ValueSetVersion> valueSetVersion = fhirParams.getFirst("valueSetVersion").isEmpty() ?
        Optional.ofNullable(valueSetVersionService.loadLastVersionByUri(fhirParams.getFirst("url").get())) :
        valueSetVersionService.query(new ValueSetVersionQueryParams()
            .setValueSetUri(fhirParams.getFirst("url").get())
            .setVersion(fhirParams.getFirst("valueSetVersion").get()))
            .findFirst();

    if (valueSetVersion.isEmpty()) {
      outcome.getIssue()
          .add(new OperationOutcomeIssue().setSeverity("error").setCode("not-found").setDetails(new CodeableConcept().setText("ValueSet version not found")));
      return null;
    }

    ValueSetVersionConceptQueryParams cParams = new ValueSetVersionConceptQueryParams()
        .setConceptCode(fhirParams.getFirst("code").orElse(null))
        .setValueSetVersionId(valueSetVersion.get().getId())
        .setCodeSystemUri(fhirParams.getFirst("system").orElse(null))
        .setCodeSystemVersion(fhirParams.getFirst("systemVersion").orElse(null));
    Optional<ValueSetVersionConcept> concept = valueSetVersionConceptService.query(cParams).findFirst();

    if (concept.isEmpty()) {
      outcome.getIssue()
          .add(new OperationOutcomeIssue().setSeverity("error").setCode("not-found").setDetails(new CodeableConcept().setText("Concept not found")));
      return null;
    }

    Parameters parameters = new Parameters();
    List<ParametersParameter> parameter = new ArrayList<>();
    String paramDisplay = fhirParams.getFirst("display").orElse(null);
    String conceptDisplay = findDisplay(concept.get(), paramDisplay);
    parameter.add(new ParametersParameter().setName("result").setValueBoolean(paramDisplay == null || paramDisplay.equals(conceptDisplay)));
    parameter.add(new ParametersParameter().setName("display").setValueString(conceptDisplay));
    if (!(paramDisplay == null || paramDisplay.equals(conceptDisplay))) {
      parameter.add(new ParametersParameter().setName("message").setValueString(String.format("The display '%s' is incorrect", paramDisplay)));
    }
    parameters.setParameter(parameter);
    return parameters;
  }

  private String findDisplay(ValueSetVersionConcept c, String paramDisplay) {
    if (paramDisplay == null) {
      return c.getDisplay() == null || c.getDisplay().getName() == null ? c.getConcept().getCode() : c.getDisplay().getName();
    }
    if (c.getDisplay() != null && paramDisplay.equals(c.getDisplay().getName())) {
      return paramDisplay;
    }
    if (CollectionUtils.isNotEmpty(c.getAdditionalDesignations())) {
      Optional<Designation> d = c.getAdditionalDesignations().stream().filter(ad -> ad != null && paramDisplay.equals(ad.getName())).findFirst();
      if (d.isPresent()) {
        return paramDisplay;
      }
    }
    return c.getDisplay() == null || c.getDisplay().getName() == null ? c.getConcept().getCode() : c.getDisplay().getName();
  }

  public void save(Optional<String> url, Optional<String> version, com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    if (url.isEmpty() || version.isEmpty()) {
      throw ApiError.TE712.toApiException();
    }
    valueSet.setUrl(url.get());
    valueSet.setVersion(version.get());
    fhirImportService.importValueSet(valueSet, false);
  }
}