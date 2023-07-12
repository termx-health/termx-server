package com.kodality.termx.fhir.valueset.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.fhir.valueset.ValueSetFhirMapper;
import com.kodality.termx.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.terminology.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Factory
@RequiredArgsConstructor
public class ValueSetValidateCodeOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final CodeSystemService codeSystemService;

  public String getResourceType() {
    return ResourceType.ValueSet.name();
  }

  public String getOperationName() {
    return "validate-code";
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent parameters) {
    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String[] parts = ValueSetFhirMapper.parseCompositeId(id.getResourceId());
    String vsId = parts[0];
    String versionNumber = parts[1];
    ValueSetVersion vsVersion = valueSetVersionService.load(vsId, versionNumber)
        .orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "ValueSet version not found"));
    Parameters resp = run(vsVersion, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public Parameters run(Parameters req) {
    String url = req.findParameter("url").map(pp -> pp.getValueUrl() != null ? pp.getValueUrl() : pp.getValueString())
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "url parameter required"));
    String version = req.findParameter("valueSetVersion").map(ParametersParameter::getValueString).orElse(null);

    ValueSetVersion vsVersion = version == null ? valueSetVersionService.loadLastVersionByUri(url) :
        valueSetVersionService.query(new ValueSetVersionQueryParams().setValueSetUri(url).setVersion(version)).findFirst().orElse(null);
    if (vsVersion == null) {
      return error("valueset version not found");
    }
    return run(vsVersion, req);
  }

  public Parameters run(ValueSetVersion vsVersion, Parameters req) {
    String code = req.findParameter("code").map(pp -> pp.getValueCode() != null ? pp.getValueCode() : pp.getValueString())
        .orElseGet(() -> req.findParameter("coding").map(pp -> pp.getValueCoding() != null ? pp.getValueCoding().getCode() : pp.getValueString())
            .orElseGet(() -> req.findParameter("codeableConcept")
                .map(cc -> cc.getValueCodeableConcept().getCoding().stream().map(Coding::getCode).collect(Collectors.joining("")))
                .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "code, coding or codeableConcept parameter required"))));

    String system = req.findParameter("system").map(pp -> pp.getValueUrl() != null ? pp.getValueUrl() : pp.getValueString())
        .map(uri -> codeSystemService.query(new CodeSystemQueryParams().setUri(uri)).findFirst()
            .orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "CodeSystem not found")).getId()
        ).orElse(null);
    String systemVersion = req.findParameter("systemVersion").map(ParametersParameter::getValueString).orElse(null);
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);

    List<ValueSetVersionConcept> concepts = valueSetVersionConceptService.expand(vsVersion.getId(), null);
    ValueSetVersionConcept concept = concepts.stream()
        .filter(c -> c.getConcept().getCode().equals(code)
                     && (system == null || system.equals(c.getConcept().getCodeSystem()))
                     && (systemVersion == null || c.getConcept().getVersions().stream()
            .anyMatch(e -> e.getVersions() != null && e.getVersions().stream().anyMatch(v -> systemVersion.equals(v.getVersion()))))
        ).findFirst().orElse(null);

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
