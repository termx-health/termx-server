package org.termx.terminology.fhir.valueset.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.commons.model.QueryResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import org.termx.terminology.Privilege;
import org.termx.core.auth.SessionStore;
import org.termx.terminology.fhir.valueset.ValueSetFhirMapper;
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionQueryParams;
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
import java.util.ArrayList;
import java.util.Arrays;
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
  private final ConceptService conceptService;

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
    SessionStore.require().checkPermitted(vsVersion.getValueSet(), Privilege.VS_READ);
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
    boolean displayValid = display == null || isDisplayValid(collectDesignations(concept), display, displayLanguage);
    parameters.addParameter(new ParametersParameter("result").setValueBoolean(displayValid));
    parameters.addParameter(new ParametersParameter("code").setValueCode(concept.getConcept().getCode()));
    parameters.addParameter(new ParametersParameter("system").setValueUri(concept.getConcept().getCodeSystemUri()));
    parameters.addParameter(new ParametersParameter("display").setValueString(conceptDisplay));
    String conceptVersion = Optional.ofNullable(concept.getConcept().getCodeSystemVersions()).orElse(List.of()).stream().findFirst().orElse(null);
    if (conceptVersion != null) {
      parameters.addParameter(new ParametersParameter("version").setValueString(conceptVersion));
    }
    if (!displayValid) {
      parameters.addParameter(new ParametersParameter("message").setValueString(String.format("The display '%s' is incorrect", display)));
    }
    if (!concept.isActive()) {
      OperationOutcome outcome = new OperationOutcome();
      outcome.setIssue(List.of(new OperationOutcomeIssue().setCode("deleted").setSeverity("warning").setDetails(new CodeableConcept().setText("Concept is inactive"))));
      parameters.addParameter(new ParametersParameter("issues").setResource(outcome));
    }
    return parameters;
  }

  /**
   * A provided display is valid when it matches the name of any of the concept's designations
   * (the preferred display or any additional designation). When a {@code displayLanguage} is
   * supplied (optionally comma-separated), the match is restricted to designations in one of those
   * languages; otherwise a match in any language is accepted — mirroring FHIR $validate-code, where
   * an unconstrained display is valid if it matches the code's designation in any language.
   */
  private boolean isDisplayValid(List<Designation> designations, String paramDisplay, String displayLanguage) {
    if (paramDisplay == null) {
      return true;
    }
    List<String> languages = StringUtils.isEmpty(displayLanguage) ? List.of() :
        Arrays.stream(displayLanguage.split(",")).map(String::trim).filter(StringUtils::isNotEmpty).toList();
    return designations.stream()
        .filter(d -> d != null && paramDisplay.equals(d.getName()))
        .anyMatch(d -> languages.isEmpty()
            || (d.getLanguage() != null && languages.stream().anyMatch(l -> d.getLanguage().equals(l) || d.getLanguage().startsWith(l + "-"))));
  }

  /**
   * Designations to validate the provided display against: the expansion's surfaced designations
   * PLUS the concept's full designation set loaded straight from the code system. The expansion
   * narrows {@code additionalDesignations} to the value set version's supported languages, but a
   * display is valid if it matches ANY designation of the code (e.g. a Russian term carried by the
   * concept while the value set only declares et/en) — so we must look past the narrowed expansion.
   */
  private List<Designation> collectDesignations(ValueSetVersionConcept c) {
    List<Designation> all = new ArrayList<>();
    if (c.getDisplay() != null) {
      all.add(c.getDisplay());
    }
    if (CollectionUtils.isNotEmpty(c.getAdditionalDesignations())) {
      all.addAll(c.getAdditionalDesignations());
    }
    String csId = c.getConcept() == null ? null : c.getConcept().getCodeSystem();
    String code = c.getConcept() == null ? null : c.getConcept().getCode();
    if (StringUtils.isNotEmpty(csId) && StringUtils.isNotEmpty(code)) {
      ConceptQueryParams cp = new ConceptQueryParams();
      cp.setCode(code);
      cp.setCodeSystem(csId);
      cp.setPermittedCodeSystems(SessionStore.require().getPermittedResourceIds(Privilege.CS_READ));
      QueryResult<Concept> queryResult = conceptService.query(cp);
      Concept concept = queryResult == null ? null : queryResult.findFirst().orElse(null);
      if (concept != null && CollectionUtils.isNotEmpty(concept.getVersions())) {
        List<Designation> designations = concept.getVersions().getFirst().getDesignations();
        if (designations != null) {
          all.addAll(designations);
        }
      }
    }
    return all;
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
