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
  private final ValueSetExpandOperation expandOperation;

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
    String url = req.findParameter("url")
        .map(pp -> pp.getValueUrl() != null ? pp.getValueUrl() : pp.getValueUri() != null ? pp.getValueUri() : pp.getValueString())
        .orElse(null);

    // tx-resource / inline: validate against a ValueSet supplied inline (the FHIR validator passes the
    // value set in the request rather than relying on stored content) — either an explicit `valueSet`
    // parameter or a `tx-resource` whose url matches the requested url.
    com.kodality.zmei.fhir.resource.terminology.ValueSet inlineVs = req.findParameter("valueSet")
        .filter(pp -> pp.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.ValueSet)
        .map(pp -> (com.kodality.zmei.fhir.resource.terminology.ValueSet) pp.getResource())
        .orElseGet(() -> findTxResourceValueSet(req, url));
    if (inlineVs != null) {
      return validateInline(inlineVs, req);
    }

    if (url == null) {
      throw new FhirException(400, IssueType.INVALID, "url parameter required");
    }
    String version = req.findParameter("valueSetVersion").map(ParametersParameter::getValueString).orElse(null);

    ValueSetVersion vsVersion = version == null ? valueSetVersionService.loadLastVersionByUri(url) :
        valueSetVersionService.query(new ValueSetVersionQueryParams().setValueSetUri(url).setVersion(version)).findFirst().orElse(null);
    if (vsVersion == null) {
      return error("valueset version not found");
    }
    return run(vsVersion, req);
  }

  /** Finds a ValueSet supplied inline via a tx-resource parameter whose url matches the requested url. */
  private static com.kodality.zmei.fhir.resource.terminology.ValueSet findTxResourceValueSet(Parameters req, String url) {
    if (req.getParameter() == null || url == null) {
      return null;
    }
    return req.getParameter().stream()
        .filter(p -> "tx-resource".equals(p.getName()))
        .map(ParametersParameter::getResource)
        .filter(r -> r instanceof com.kodality.zmei.fhir.resource.terminology.ValueSet)
        .map(r -> (com.kodality.zmei.fhir.resource.terminology.ValueSet) r)
        .filter(vs -> url.equals(vs.getUrl()))
        .findFirst().orElse(null);
  }

  /**
   * Validates a code against an inline ValueSet by expanding it (reusing {@link ValueSetExpandOperation})
   * and checking membership in the expansion — the path used when the value set is supplied in the request
   * rather than stored.
   */
  private Parameters validateInline(com.kodality.zmei.fhir.resource.terminology.ValueSet inlineVs, Parameters req) {
    String displayLanguage = req.findParameter("displayLanguage").map(p -> p.getValueCode() != null ? p.getValueCode() : p.getValueString()).orElse(null);
    String code = req.findParameter("code").map(p -> p.getValueCode() != null ? p.getValueCode() : p.getValueString()).orElse(null);
    String system = findSystem(req);
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);
    if (req.findParameter("coding").isPresent()) {
      Coding coding = req.findParameter("coding").map(ParametersParameter::getValueCoding).orElse(null);
      code = coding != null && coding.getCode() != null ? coding.getCode() : code;
      system = coding != null && coding.getSystem() != null ? coding.getSystem() : system;
      display = coding != null && coding.getDisplay() != null ? coding.getDisplay() : display;
    }
    if (req.findParameter("codeableConcept").isPresent()) {
      CodeableConcept cc = req.findParameter("codeableConcept").map(ParametersParameter::getValueCodeableConcept).orElse(null);
      Coding coding = cc != null && cc.getCoding() != null ? cc.getCoding().stream().findFirst().orElse(null) : null;
      code = coding != null && coding.getCode() != null ? coding.getCode() : code;
      system = coding != null && coding.getSystem() != null ? coding.getSystem() : system;
    }
    if (code == null) {
      throw new FhirException(400, IssueType.INVALID, "code, coding or codeableConcept parameter required");
    }

    Parameters expandReq = new Parameters();
    expandReq.addParameter(new ParametersParameter("valueSet").setResource(inlineVs));
    expandReq.addParameter(new ParametersParameter("includeDesignations").setValueBoolean(true));
    if (displayLanguage != null) {
      expandReq.addParameter(new ParametersParameter("displayLanguage").setValueCode(displayLanguage));
    }
    com.kodality.zmei.fhir.resource.terminology.ValueSet expanded = expandOperation.run(expandReq);
    String finalCode = code;
    String finalSystem = system;
    var match = Optional.ofNullable(expanded.getExpansion())
        .map(com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion::getContains).orElse(List.of()).stream()
        .filter(c -> finalCode.equals(c.getCode()) && (finalSystem == null || finalSystem.equals(c.getSystem())))
        .findFirst().orElse(null);

    Parameters resp = new Parameters();
    if (match == null) {
      resp.addParameter(new ParametersParameter("result").setValueBoolean(false));
      resp.addParameter(new ParametersParameter("code").setValueCode(code));
      if (system != null) {
        resp.addParameter(new ParametersParameter("system").setValueUri(system));
      }
      resp.addParameter(new ParametersParameter("message").setValueString(
          String.format("The provided code %s is not in the value set", (system != null ? system + "#" : "") + code)));
      return resp;
    }
    String finalDisplay = display;
    boolean displayValid = finalDisplay == null || finalDisplay.equals(match.getDisplay())
        || Optional.ofNullable(match.getDesignation()).orElse(List.of()).stream().anyMatch(d -> finalDisplay.equals(d.getValue()));
    resp.addParameter(new ParametersParameter("result").setValueBoolean(displayValid));
    resp.addParameter(new ParametersParameter("code").setValueCode(match.getCode()));
    if (match.getSystem() != null) {
      resp.addParameter(new ParametersParameter("system").setValueUri(match.getSystem()));
    }
    resp.addParameter(new ParametersParameter("display").setValueString(match.getDisplay()));
    if (!displayValid) {
      resp.addParameter(new ParametersParameter("message").setValueString(String.format("The display '%s' is incorrect", display)));
    }
    return resp;
  }

  public Parameters run(ValueSetVersion vsVersion, Parameters req) {
    Parameters response = doRun(vsVersion, req);
    // FHIR $validate-code echoes the codeableConcept it was given back to the caller (it isn't reconstructed
    // from the matched code), so surface the request's codeableConcept on every response shape.
    if (response != null) {
      req.findParameter("codeableConcept")
          .filter(cc -> response.findParameter("codeableConcept").isEmpty())
          .ifPresent(response::addParameter);
    }
    return response;
  }

  private Parameters doRun(ValueSetVersion vsVersion, Parameters req) {
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
        .filter(c -> systemMatches(c, finalSystem))
        .filter(c -> finalVersion == null || (c.getConcept().getCodeSystemVersions() != null && c.getConcept().getCodeSystemVersions().contains(finalVersion)))
        .findFirst().orElse(null);

    // Graceful degradation for an unknown systemVersion: the requested version names a code system
    // version that does not exist (the code itself IS in the value set, just under a different version).
    // Per the FHIR tx ecosystem this is a 200 with result=false plus an UNKNOWN_CODESYSTEM_VERSION issue
    // listing the valid versions — not a 404/"not in value set".
    if (concept == null && finalVersion != null && finalSystem != null) {
      ValueSetVersionConcept anyVersion = vsConcepts.stream()
          .filter(c -> finalCode.equals(c.getConcept().getCode()))
          .filter(c -> systemMatches(c, finalSystem))
          .findFirst().orElse(null);
      if (anyVersion != null) {
        List<String> available = Optional.ofNullable(anyVersion.getConcept().getCodeSystemVersions()).orElse(List.of());
        if (!available.contains(finalVersion)) {
          return unknownSystemVersion(anyVersion, finalSystem, finalVersion, available, display, displayLanguage);
        }
      }
    }

    if (concept == null) {
      return notInValueSet(finalCode, finalSystem, vsConcepts);
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
   * Whether a value set member satisfies the requested {@code system}. A member matches its own code system
   * uri, but ALSO its base code system uri: a {@code valueset-supplement}-bound value set carries the
   * supplement as the member's code system, yet the codes belong to (and are presented/validated against) the
   * base — so {@code $validate-code} with {@code system=<base>} must still find them. A null system matches any.
   */
  private static boolean systemMatches(ValueSetVersionConcept c, String system) {
    return system == null
        || system.equals(c.getConcept().getCodeSystemUri())
        || system.equals(c.getConcept().getBaseCodeSystemUri());
  }

  /**
   * Builds the response for a requested {@code systemVersion} that does not exist: result=false, the code's
   * display/system/available version echoed, and an {@code issues} OperationOutcome carrying the
   * UNKNOWN_CODESYSTEM_VERSION error (with the valid versions) plus the versionless-include mismatch warning.
   * Mirrors the FHIR tx ecosystem's "graceful degradation" — a 200, not a 4xx.
   */
  private Parameters unknownSystemVersion(ValueSetVersionConcept concept, String system, String requestedVersion,
                                          List<String> availableVersions, String display, String displayLanguage) {
    String availableVersion = availableVersions.stream().findFirst().orElse(null);
    String message = String.format(
        "A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: %s",
        system, requestedVersion, String.join(", ", availableVersions));

    OperationOutcomeIssue notFound = new OperationOutcomeIssue()
        .setSeverity("error").setCode("not-found")
        .setDetails(new CodeableConcept(new Coding("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type", "not-found")).setText(message))
        .setLocation(List.of("system")).setExpression(List.of("system"));
    OperationOutcomeIssue mismatch = new OperationOutcomeIssue()
        .setSeverity("warning").setCode("invalid")
        .setDetails(new CodeableConcept(new Coding("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type", "vs-invalid")).setText(String.format(
            "The code system '%s' version '%s' for the versionless include in the ValueSet include is different to the one in the value ('%s')",
            system, availableVersion, requestedVersion)))
        .setLocation(List.of("version")).setExpression(List.of("version"));

    OperationOutcome outcome = new OperationOutcome();
    outcome.setIssue(List.of(notFound, mismatch));

    Parameters parameters = new Parameters();
    parameters.addParameter(new ParametersParameter("code").setValueCode(concept.getConcept().getCode()));
    String conceptDisplay = findDisplay(concept, display, displayLanguage);
    if (conceptDisplay != null) {
      parameters.addParameter(new ParametersParameter("display").setValueString(conceptDisplay));
    }
    parameters.addParameter(new ParametersParameter("issues").setResource(outcome));
    parameters.addParameter(new ParametersParameter("message").setValueString(message));
    parameters.addParameter(new ParametersParameter("result").setValueBoolean(false));
    parameters.addParameter(new ParametersParameter("system").setValueUri(system));
    if (availableVersion != null) {
      parameters.addParameter(new ParametersParameter("version").setValueString(availableVersion));
    }
    parameters.addParameter(new ParametersParameter("x-caused-by-unknown-system").setValueCanonical(system + "|" + requestedVersion));
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

  /**
   * Response for a code that is not in the value set: 200 with result=false, the code/system/version echoed,
   * and a structured {@code issues} OperationOutcome ({@code code-invalid} with tx-issue-type {@code not-in-vs}
   * and {@code invalid-code} at {@code code}) — the FHIR tx ecosystem shape, instead of a flat message only.
   */
  private static Parameters notInValueSet(String code, String system, List<ValueSetVersionConcept> vsConcepts) {
    String version = vsConcepts.stream()
        .filter(c -> system == null || systemMatches(c, system))
        .map(c -> c.getConcept().getCodeSystemVersions())
        .filter(java.util.Objects::nonNull).flatMap(List::stream)
        .findFirst().orElse(null);
    String message = String.format("The provided code %s is not in the value set", (system != null ? system + "#" : "") + code);
    Parameters result = new Parameters()
        .addParameter(new ParametersParameter("result").setValueBoolean(false))
        .addParameter(new ParametersParameter("code").setValueCode(code));
    if (system != null) {
      result.addParameter(new ParametersParameter("system").setValueUri(system));
    }
    if (version != null) {
      result.addParameter(new ParametersParameter("version").setValueString(version));
    }
    result.addParameter(new ParametersParameter("message").setValueString(message));
    result.addParameter(new ParametersParameter("issues").setResource(
        org.termx.terminology.fhir.TxIssues.outcome(
            org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "not-in-vs", message, "code"),
            org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "invalid-code", message, "code"))));
    return result;
  }

}
