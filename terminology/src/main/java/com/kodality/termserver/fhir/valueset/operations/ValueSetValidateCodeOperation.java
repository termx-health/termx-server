package com.kodality.termserver.fhir.valueset.operations;

import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termserver.terminology.valueset.ValueSetVersionService;
import com.kodality.termserver.terminology.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionConceptQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.CollectionUtils;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Factory
@RequiredArgsConstructor
public class ValueSetValidateCodeOperation implements TypeOperationDefinition {
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;

  public String getResourceType() {
    return ResourceType.ValueSet.name();
  }

  public String getOperationName() {
    return "validate-code";
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public Parameters run(Parameters req) {
    String url = req.findParameter("url").map(ParametersParameter::getValueUri)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "url parameter required"));
    String code = req.findParameter("code").map(ParametersParameter::getValueCode)
        .orElse(req.findParameter("coding").map(ParametersParameter::getValueCoding).map(Coding::getCode)
        .orElse(req.findParameter("codeableConcept").map(ParametersParameter::getValueCodeableConcept).map(CodeableConcept::getCoding)
            .map(c -> c.stream().map(Coding::getCode).collect(Collectors.joining("")))
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "code, coding or codeableConcept parameter required"))));
    String version = req.findParameter("valueSetVersion").map(ParametersParameter::getValueString).orElse(null);
    String system = req.findParameter("system").map(ParametersParameter::getValueUri).orElse(null);
    String systemVersion = req.findParameter("systemVersion").map(ParametersParameter::getValueString).orElse(null);
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);

    ValueSetVersion vsVersion = version == null ? valueSetVersionService.loadLastVersionByUri(url) :
        valueSetVersionService.query(new ValueSetVersionQueryParams().setValueSetUri(url).setVersion(version)).findFirst().orElse(null);

    if (vsVersion == null) {
      return error("valueset version not found");
    }

    ValueSetVersionConceptQueryParams cParams = new ValueSetVersionConceptQueryParams()
        .setConceptCode(code)
        .setValueSetVersionId(vsVersion.getId())
        .setCodeSystemUri(system)
        .setCodeSystemVersion(systemVersion);
    ValueSetVersionConcept concept = valueSetVersionConceptService.query(cParams).findFirst().orElse(null);
    if (concept == null) {
      return error("invalid code");
    }

    Parameters parameters = new Parameters();
    String conceptDisplay = findDisplay(concept, display);
    parameters.addParameter(new ParametersParameter("result").setValueBoolean(display == null || display.equals(conceptDisplay)));
    parameters.addParameter(new ParametersParameter("display").setValueString(conceptDisplay));
    if (display != null && !display.equals(conceptDisplay)) {
      parameters.addParameter(new ParametersParameter("message").setValueString(String.format("The display '%s' is incorrect", display)));
    }
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

  private static Parameters error(String message) {
    return new Parameters()
        .addParameter(new ParametersParameter("result").setValueBoolean(false))
        .addParameter(new ParametersParameter("message").setValueString(message));
  }

}
