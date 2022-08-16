package com.kodality.termserver.fhir.valueset;

import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.ts.valueset.ValueSetService;
import com.kodality.termserver.ts.valueset.ValueSetVersionService;
import com.kodality.termserver.ts.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionConcept;
import com.kodality.termserver.valueset.ValueSetVersionConceptQueryParams;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.OperationOutcome.OperationOutcomeIssue;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  private final UserPermissionService userPermissionService;

  public com.kodality.zmei.fhir.resource.terminology.ValueSet get(Long valueSetVersionId) {
    ValueSet valueSet = valueSetService.query(new ValueSetQueryParams().setVersionId(valueSetVersionId)).findFirst().orElse(null);
    if (valueSet == null) {
      return null;
    }
    userPermissionService.checkPermitted(valueSet.getId(), "ValueSet", "view");
    ValueSetVersion version = valueSetVersionService.load(valueSetVersionId);
    return mapper.toFhir(valueSet, version);
  }

  public com.kodality.zmei.fhir.resource.terminology.ValueSet expand(Map<String, List<String>> params, OperationOutcome outcome) {
    outcome.setIssue(new ArrayList<>());
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    if (fhirParams.getFirst("url").isEmpty() || fhirParams.getFirst("valueSetVersion").isEmpty()) {
      outcome.getIssue().addAll(Stream.of("url", "valueSetVersion").filter(key -> fhirParams.getFirst(key).isEmpty())
          .map(key -> new OperationOutcomeIssue().setSeverity("error").setCode("required")
              .setDetails(new CodeableConcept().setText(String.format("Parameter '%s' not provided", key)))).toList());
      return null;
    }
    ValueSetVersionQueryParams vsvParams = new ValueSetVersionQueryParams();
    vsvParams.setValueSetUri(fhirParams.getFirst("url").get());
    vsvParams.setVersion(fhirParams.getFirst("valueSetVersion").get());
    vsvParams.setDecorated(true);
    vsvParams.setLimit(1);
    ValueSetVersion version = valueSetVersionService.query(vsvParams).findFirst().orElse(null);
    if (version == null) {
      outcome.getIssue()
          .add(new OperationOutcomeIssue().setSeverity("error").setCode("not-found").setDetails(new CodeableConcept().setText("Value set version not found")));
      return null;
    }

    ValueSetQueryParams vsParams = new ValueSetQueryParams();
    vsParams.setVersionId(version.getId());
    vsParams.setLimit(1);
    ValueSet valueSet = valueSetService.query(vsParams).findFirst().orElse(null);
    if (valueSet == null) {
      outcome.getIssue()
          .add(new OperationOutcomeIssue().setSeverity("error").setCode("not-found").setDetails(new CodeableConcept().setText("Value set not found")));
      return null;
    }
    userPermissionService.checkPermitted(valueSet.getId(), "ValueSet", "view");

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
        valueSetVersionService.query(new ValueSetVersionQueryParams().setValueSetUri(fhirParams.getFirst("url").get()).setVersion(fhirParams.getFirst("valueSetVersion").get())).findFirst();

    if (valueSetVersion.isEmpty()) {
      outcome.getIssue()
          .add(new OperationOutcomeIssue().setSeverity("error").setCode("not-found").setDetails(new CodeableConcept().setText("ValueSet version not found")));
      return null;
    }
    userPermissionService.checkPermitted(valueSetVersion.get().getValueSet(), "ValueSet", "view");

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
    List<Parameter> parameter = new ArrayList<>();
    String paramDisplay = fhirParams.getFirst("display").orElse(null);
    String conceptDisplay = findDisplay(concept.get(), paramDisplay);
    parameter.add(new Parameter().setName("result").setValueBoolean(paramDisplay == null || paramDisplay.equals(conceptDisplay)));
    parameter.add(new Parameter().setName("display").setValueString(conceptDisplay));
    if (!(paramDisplay == null || paramDisplay.equals(conceptDisplay))) {
      parameter.add(new Parameter().setName("message").setValueString(String.format("The display '%s' is incorrect", paramDisplay)));
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
}
