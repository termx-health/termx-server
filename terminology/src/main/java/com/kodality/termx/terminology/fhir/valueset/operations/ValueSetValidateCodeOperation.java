package com.kodality.termx.terminology.fhir.valueset.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.terminology.fhir.valueset.ValueSetFhirMapper;
import com.kodality.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import com.kodality.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.OperationOutcome.OperationOutcomeIssue;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import java.util.Optional;
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
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "ValueSet version not found"));
    Parameters resp = run(vsVersion, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public Parameters run(Parameters req) {
    String url =
        req.findParameter("url").map(pp -> pp.getValueUrl() != null ? pp.getValueUrl() : pp.getValueUri() != null ? pp.getValueUri() : pp.getValueString())
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
    SessionStore.require().checkPermitted(vsVersion.getValueSet(), Privilege.VS_VIEW);
    String code = req.findParameter("code").map(p -> p.getValueCode() != null ? p.getValueCode() : p.getValueString()).orElse(null);
    String system = findSystem(req);
    String version = req.findParameter("systemVersion").map(ParametersParameter::getValueString).orElse(null);
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);
    String displayLanguage = req.findParameter("displayLanguage").map(ParametersParameter::getValueCode)
            .orElse(req.findParameter("displayLanguage").map(ParametersParameter::getValueString).orElse(null));

    if (req.findParameter("coding").isPresent()) {
      Coding coding = req.findParameter("coding").map(ParametersParameter::getValueCoding).orElse(null);
      code = coding != null && coding.getCode() != null ? coding.getCode() : code;
      system = coding != null && coding.getSystem() != null ? coding.getSystem() : system;
      version = coding != null && coding.getVersion() != null ? coding.getVersion() : version;
    }

    if (req.findParameter("codeableConcept").isPresent()) {
      CodeableConcept codeableConcept = req.findParameter("codeableConcept").map(ParametersParameter::getValueCodeableConcept).orElse(null);
      Coding coding = codeableConcept != null && codeableConcept.getCoding() != null ? codeableConcept.getCoding().stream().findFirst().orElse(null) : null;
      code = coding != null && coding.getCode() != null ? coding.getCode() : code;
      system = coding != null && coding.getSystem() != null ? coding.getSystem() : system;
      version = coding != null && coding.getVersion() != null ? coding.getVersion() : version;
    }

    String finalCode = code;
    String finalSystem = system;
    String finalVersion = version;

    if (finalCode == null) {
      throw new FhirException(400, IssueType.INVALID, "code, coding or codeableConcept parameter required");
    }

    List<ValueSetVersionConcept> vsConcepts = valueSetVersionConceptService.expand(vsVersion, displayLanguage);
    ValueSetVersionConcept concept = vsConcepts.stream()
        .filter(c -> finalCode.equals(c.getConcept().getCode()))
        .filter(c -> finalSystem == null || finalSystem.equals(c.getConcept().getCodeSystemUri()))
        .filter(c -> finalVersion == null || (c.getConcept().getCodeSystemVersions() != null && c.getConcept().getCodeSystemVersions().contains(finalVersion)))
        .findFirst().orElse(null);

    if (concept == null) {
      return error(String.format("The provided code %s is not in the value set", (system != null ? system  + "#" : "") + code));
    }

    Parameters parameters = new Parameters();
    String conceptDisplay = findDisplay(concept, display, displayLanguage);
    parameters.addParameter(new ParametersParameter("result").setValueBoolean(display == null || display.equals(conceptDisplay)));
    parameters.addParameter(new ParametersParameter("code").setValueCode(concept.getConcept().getCode()));
    parameters.addParameter(new ParametersParameter("system").setValueUri(concept.getConcept().getCodeSystemUri()));
    parameters.addParameter(new ParametersParameter("display").setValueString(conceptDisplay));
    String conceptVersion = Optional.ofNullable(concept.getConcept().getCodeSystemVersions()).orElse(List.of()).stream().findFirst().orElse(null);
    if (conceptVersion != null) {
      parameters.addParameter(new ParametersParameter("version").setValueString(conceptVersion));
    }
    if (display != null && !display.equals(conceptDisplay)) {
      parameters.addParameter(new ParametersParameter("message").setValueString(String.format("The display '%s' is incorrect", display)));
    }
    if (!concept.isActive()) {
      OperationOutcome outcome = new OperationOutcome();
      outcome.setIssue(List.of(new OperationOutcomeIssue().setCode("deleted").setSeverity("warning").setDetails(new CodeableConcept().setText("Concept is inactive"))));
      parameters.addParameter(new ParametersParameter("issues").setResource(outcome));
    }
    return parameters;
  }

  private String findSystem(Parameters req) {
    return req.findParameter("system").map(p ->
        p.getValueUrl() != null ? p.getValueUrl() :
            p.getValueUri() != null ? p.getValueUri() :
                p.getValueCanonical() != null ? p.getValueCanonical() :
                    p.getValueString()).orElse(null);
  }

  private String findDisplay(ValueSetVersionConcept c, String paramDisplay, String displayLanguage) {
    // 1. Determine the language to use for validation/return
    String validationLanguage = (displayLanguage != null) ? displayLanguage : c.getDisplay().getLanguage();

    // 2. Handle the case where no specific display is requested (paramDisplay == null)
    if (paramDisplay == null) {
      // Return the primary display name if available, otherwise return the concept code.
      return c.getDisplay() == null || StringUtils.isEmpty(c.getDisplay().getName()) ? c.getConcept().getCode() : c.getDisplay().getName();
    }

    // 3. Check the primary display for a match by text and language
    if (c.getDisplay() != null && paramDisplay.equals(c.getDisplay().getName()) && c.getDisplay().getLanguage().equals(validationLanguage)) {
      return paramDisplay;
    }

    // 4. Check additional designations for a match on name AND language
    if (CollectionUtils.isNotEmpty(c.getAdditionalDesignations())) {
      Optional<Designation> matchingDesignation = c.getAdditionalDesignations().stream()
          .filter(ad -> ad != null &&
              ("display".equals(ad.getDesignationType()) || ad.getDesignationType() == null) && // Matches the designations of the type "display"
              validationLanguage.equals(ad.getLanguage())) // Matches the required language
          .findFirst();

      // if matching designation present return it name
      if (matchingDesignation.isPresent()) {
        return matchingDesignation.get().getName();
      }
    }

    // 5. Fallback: No match was found for the requested paramDisplay and language.
    // Return the default display value (primary display or code).
    return c.getDisplay() == null || StringUtils.isEmpty(c.getDisplay().getName()) ? c.getConcept().getCode() : c.getDisplay().getName();
  }

  private static Parameters error(String message) {
    return new Parameters()
        .addParameter(new ParametersParameter("result").setValueBoolean(false))
        .addParameter(new ParametersParameter("message").setValueString(message));
  }

}
