package org.termx.terminology.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import org.termx.terminology.Privilege;
import org.termx.core.auth.SessionStore;
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.concept.ConceptUtil;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemQueryParams;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.CodeSystemVersionQueryParams;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.Designation;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import io.micronaut.context.annotation.Factory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Factory
@RequiredArgsConstructor
public class CodeSystemValidateCodeOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private static final String UCUM = "ucum";
  private static final String UCUM_URI = "http://unitsofmeasure.org";

  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;

  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  public String getOperationName() {
    return "validate-code";
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent parameters) {
    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String[] parts = CodeSystemFhirMapper.parseCompositeId(id.getResourceId());
    String csId = parts[0];
    String versionNumber = parts[1];
    CodeSystemVersion csv = codeSystemVersionService.load(csId, versionNumber)
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "Concept version not found"));
    Parameters resp = run(csv.getCodeSystem(), csv.getId(), null, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public Parameters run(Parameters req) {
    String url = req.findParameter("url")
        .map(pp -> StringUtils.firstNonBlank(pp.getValueUrl(), pp.getValueCanonical(), pp.getValueUri(), pp.getValueString()))
        .or(() -> req.findParameter("system")
            .map(pp -> StringUtils.firstNonBlank(pp.getValueUrl(), pp.getValueCanonical(), pp.getValueUri(), pp.getValueString())))
        // A url-less $validate-code infers the code system from the supplied coding / codeableConcept system, so a
        // coding naming (say) a supplement code system resolves to that CS and degrades to a 200 invalid-data rather
        // than a 400 "url parameter required".
        .or(() -> codingSystem(req))
        .orElse(null);

    // tx-resource / inline: validate against a CodeSystem supplied inline (the FHIR validator passes the
    // code system in the request rather than relying on stored content — e.g. urn:iso:std:iso:3166) —
    // either an explicit `codeSystem` parameter or a `tx-resource` whose url matches the requested url.
    com.kodality.zmei.fhir.resource.terminology.CodeSystem inlineCs = req.findParameter("codeSystem")
        .filter(pp -> pp.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem)
        .map(pp -> (com.kodality.zmei.fhir.resource.terminology.CodeSystem) pp.getResource())
        .orElseGet(() -> findTxResourceCodeSystem(req, url));
    if (inlineCs != null) {
      return validateInline(inlineCs, req);
    }

    if (url == null) {
      throw new FhirException(400, IssueType.INVALID, "url parameter required");
    }
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);

    CodeSystemQueryParams csp = new CodeSystemQueryParams();
    csp.setUri(url);
    csp.setLimit(1);
    CodeSystem cs = codeSystemService.query(csp).findFirst().orElse(null);
    if (cs == null && UCUM_URI.equals(url)) {
      cs = codeSystemService.load(UCUM).orElse(null);
    }
    if (cs == null) {
      return error("CodeSystem not found by url " + url);
    }

    CodeSystemVersion csv = null;
    if (version != null) {
      CodeSystemVersionQueryParams csvp = new CodeSystemVersionQueryParams();
      csvp.setCodeSystem(cs.getId());
      csvp.setVersion(version);
      csvp.setStatus(PublicationStatus.active);
      csvp.setLimit(1);
      csv = codeSystemVersionService.query(csvp).findFirst().orElse(null);
    }

    // When the version doesn't resolve to a stored TermX version, pass it through as-is rather than
    // failing. External providers — notably SNOMED, whose `version` is the edition URI
    // (http://snomed.info/sct/<module>[/version/<date>]) and is never a stored TermX version — derive
    // their branch from this string (see SnomedCodeSystemProvider), mirroring CodeSystem/$lookup. A
    // genuinely bogus version then simply yields no concept ("code is invalid") downstream.
    Long versionId = csv == null ? null : csv.getId();
    String versionUri = csv == null ? version : null;
    return run(cs.getId(), versionId, versionUri, req);
  }

  private Parameters run(String csId, Long versionId, String versionUri, Parameters req) {
    String code = req.findParameter("code").map(pp -> StringUtils.firstNonBlank(pp.getValueCode(), pp.getValueString()))
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "code parameter required"));
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);
    String displayLanguage = req.findParameter("displayLanguage")
        .map(pp -> StringUtils.firstNonBlank(pp.getValueCode(), pp.getValueString()))
        .orElse(null);


    ConceptQueryParams cp = new ConceptQueryParams();
    cp.setCode(code);
    cp.setCodeSystem(csId);
    cp.setCodeSystemVersionId(versionId);
    cp.setCodeSystemVersion(versionUri);
    cp.setIncludeSupplement(true);
    cp.setDisplayLanguage(displayLanguage);
    cp.setUseSupplement(extractUseSupplement(req));
    cp.setPermittedCodeSystems(SessionStore.require().getPermittedResourceIds(Privilege.CS_READ));

    Concept concept = conceptService.query(cp).findFirst().orElse(null);
    if (concept == null) {
      return invalidCode(csId, code, versionUri);
    }

    Set<String> validDisplays = extractDisplays(concept, displayLanguage);
    String conceptDisplay = validDisplays.stream().findFirst().orElse(null);
    if (display != null && !validDisplays.contains(display)) {
      return new Parameters()
          .addParameter(new ParametersParameter("result").setValueBoolean(false))
          .addParameter(new ParametersParameter("display").setValueString(conceptDisplay))
          .addParameter(new ParametersParameter("message").setValueString("The display '" + display + "' is incorrect"));
    }

    // A successful validation echoes the resolved coding so the client can confirm what was validated.
    CodeSystem cs = codeSystemService.load(csId).orElse(null);
    Parameters result = new Parameters()
        .addParameter(new ParametersParameter("result").setValueBoolean(true))
        .addParameter(new ParametersParameter("code").setValueCode(code));
    if (cs != null && StringUtils.isNotEmpty(cs.getUri())) {
      result.addParameter(new ParametersParameter("system").setValueUri(cs.getUri()));
    }
    if (StringUtils.isNotEmpty(conceptDisplay)) {
      result.addParameter(new ParametersParameter("display").setValueString(conceptDisplay));
    }
    if (StringUtils.isNotEmpty(versionUri)) {
      result.addParameter(new ParametersParameter("version").setValueString(versionUri));
    }
    return result;
  }

  private static Parameters error(String message) {
    return new Parameters()
        .addParameter(new ParametersParameter("result").setValueBoolean(false))
        .addParameter(new ParametersParameter("message").setValueString(message));
  }

  /**
   * Response for a code that is not in the code system: 200 with result=false, the resolved system/version
   * echoed, and a structured {@code issues} OperationOutcome ({@code code-invalid} / tx-issue-type
   * {@code invalid-code} at {@code code}) — the FHIR tx ecosystem shape, instead of a flat message.
   */
  private Parameters invalidCode(String csId, String code, String versionUri) {
    CodeSystem cs = codeSystemService.load(csId).orElse(null);
    String csUri = cs != null && StringUtils.isNotEmpty(cs.getUri()) ? cs.getUri() : csId;
    String version = versionUri;
    if (version == null) {
      CodeSystemVersion last = codeSystemVersionService.loadLastVersion(csId);
      version = last == null ? null : last.getVersion();
    }
    String message = "Unknown code '" + code + "' in the CodeSystem '" + csUri + "'"
        + (StringUtils.isNotEmpty(version) ? " version '" + version + "'" : "");
    Parameters result = new Parameters()
        .addParameter(new ParametersParameter("result").setValueBoolean(false))
        .addParameter(new ParametersParameter("code").setValueCode(code));
    if (cs != null && StringUtils.isNotEmpty(cs.getUri())) {
      result.addParameter(new ParametersParameter("system").setValueUri(cs.getUri()));
    }
    if (StringUtils.isNotEmpty(version)) {
      result.addParameter(new ParametersParameter("version").setValueString(version));
    }
    result.addParameter(new ParametersParameter("message").setValueString(message));
    result.addParameter(new ParametersParameter("issues").setResource(
        org.termx.terminology.fhir.TxIssues.outcome(
            org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "invalid-code", message, "code"))));
    return result;
  }

  /** Finds a CodeSystem supplied inline via a tx-resource parameter whose url matches the requested url. */
  /** The code system url named by the request's {@code coding} / {@code codeableConcept} system — the fallback
   *  source of the system for a url-less {@code $validate-code}. */
  private static java.util.Optional<String> codingSystem(Parameters req) {
    var coding = req.findParameter("coding").map(ParametersParameter::getValueCoding).orElse(null);
    if (coding != null && StringUtils.isNotEmpty(coding.getSystem())) {
      return java.util.Optional.of(coding.getSystem());
    }
    var cc = req.findParameter("codeableConcept").map(ParametersParameter::getValueCodeableConcept).orElse(null);
    if (cc != null && cc.getCoding() != null) {
      return cc.getCoding().stream().map(c -> c.getSystem()).filter(StringUtils::isNotEmpty).findFirst();
    }
    return java.util.Optional.empty();
  }

  private static com.kodality.zmei.fhir.resource.terminology.CodeSystem findTxResourceCodeSystem(Parameters req, String url) {
    if (req.getParameter() == null || url == null) {
      return null;
    }
    return req.getParameter().stream()
        .filter(p -> "tx-resource".equals(p.getName()))
        .map(ParametersParameter::getResource)
        .filter(r -> r instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem)
        .map(r -> (com.kodality.zmei.fhir.resource.terminology.CodeSystem) r)
        .filter(cs -> url.equals(cs.getUrl()))
        .findFirst().orElse(null);
  }

  /**
   * Validates a code against an inline CodeSystem — the path used when the code system is supplied in the
   * request (tx-resource / codeSystem parameter) rather than stored. Walks the inline concept tree by code
   * and validates any provided display against the concept's display / designations / definition.
   */
  private Parameters validateInline(com.kodality.zmei.fhir.resource.terminology.CodeSystem inlineCs, Parameters req) {
    String code = req.findParameter("code").map(pp -> StringUtils.firstNonBlank(pp.getValueCode(), pp.getValueString())).orElse(null);
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);
    String displayLanguage = req.findParameter("displayLanguage")
        .map(pp -> StringUtils.firstNonBlank(pp.getValueCode(), pp.getValueString())).orElse(null);
    if (req.findParameter("coding").isPresent()) {
      var coding = req.findParameter("coding").map(ParametersParameter::getValueCoding).orElse(null);
      code = coding != null && coding.getCode() != null ? coding.getCode() : code;
      display = coding != null && coding.getDisplay() != null ? coding.getDisplay() : display;
    }
    if (req.findParameter("codeableConcept").isPresent()) {
      var cc = req.findParameter("codeableConcept").map(ParametersParameter::getValueCodeableConcept).orElse(null);
      var coding = cc != null && cc.getCoding() != null ? cc.getCoding().stream().findFirst().orElse(null) : null;
      code = coding != null && coding.getCode() != null ? coding.getCode() : code;
      display = coding != null && coding.getDisplay() != null ? coding.getDisplay() : display;
    }
    if (code == null) {
      throw new FhirException(400, IssueType.INVALID, "code, coding or codeableConcept parameter required");
    }

    // A supplement code system is not a usable Coding.system: the reference degrades to a 200 result=false with an
    // invalid-data issue, not a 400. (A url-less validate naming a supplement resolves to the bundled supplement CS.)
    if ("supplement".equals(inlineCs.getContent())) {
      String csRef = inlineCs.getUrl() + (StringUtils.isNotEmpty(inlineCs.getVersion()) ? "|" + inlineCs.getVersion() : "");
      String message = "CodeSystem " + csRef + " is a supplement, so can't be used as a value in Coding.system";
      String systemLoc = req.findParameter("codeableConcept").isPresent() ? "CodeableConcept.coding[0].system" : "Coding.system";
      Parameters supplement = new Parameters()
          .addParameter(new ParametersParameter("result").setValueBoolean(false))
          .addParameter(new ParametersParameter("code").setValueCode(code));
      if (StringUtils.isNotEmpty(inlineCs.getUrl())) {
        supplement.addParameter(new ParametersParameter("system").setValueUri(inlineCs.getUrl()));
      }
      supplement.addParameter(new ParametersParameter("issues").setResource(org.termx.terminology.fhir.TxIssues.outcome(
          org.termx.terminology.fhir.TxIssues.issue("error", "invalid", "invalid-data", message, systemLoc))));
      supplement.addParameter(new ParametersParameter("message").setValueString(message));
      return supplement;
    }

    String finalCode = code;
    var concept = findInlineConcept(inlineCs.getConcept(), finalCode);
    if (concept == null) {
      // Code not in the inline code system: the full tx-ecosystem shape (200, result=false, code/system/version
      // echoed, a structured invalid-code issue), mirroring the stored-content invalidCode() builder — not a flat
      // message, which omits the issues/system/version the reference server returns.
      String csUri = inlineCs.getUrl();
      String version = inlineCs.getVersion();
      String message = "Unknown code '" + code + "' in the CodeSystem '" + csUri + "'"
          + (StringUtils.isNotEmpty(version) ? " version '" + version + "'" : "");
      Parameters notFound = new Parameters()
          .addParameter(new ParametersParameter("result").setValueBoolean(false))
          .addParameter(new ParametersParameter("code").setValueCode(code));
      if (StringUtils.isNotEmpty(csUri)) {
        notFound.addParameter(new ParametersParameter("system").setValueUri(csUri));
      }
      if (StringUtils.isNotEmpty(version)) {
        notFound.addParameter(new ParametersParameter("version").setValueString(version));
      }
      notFound.addParameter(new ParametersParameter("message").setValueString(message));
      notFound.addParameter(new ParametersParameter("issues").setResource(
          org.termx.terminology.fhir.TxIssues.outcome(
              org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "invalid-code", message, "code"))));
      return notFound;
    }

    Set<String> validDisplays = inlineDisplays(concept, displayLanguage, inlineCs);
    String conceptDisplay = validDisplays.stream().findFirst().orElse(null);
    if (display != null && !validDisplays.contains(display)) {
      return new Parameters()
          .addParameter(new ParametersParameter("result").setValueBoolean(false))
          .addParameter(new ParametersParameter("code").setValueCode(code))
          .addParameter(new ParametersParameter("display").setValueString(conceptDisplay))
          .addParameter(new ParametersParameter("message").setValueString("The display '" + display + "' is incorrect"));
    }

    Parameters result = new Parameters()
        .addParameter(new ParametersParameter("result").setValueBoolean(true))
        .addParameter(new ParametersParameter("code").setValueCode(code));
    if (StringUtils.isNotEmpty(inlineCs.getUrl())) {
      result.addParameter(new ParametersParameter("system").setValueUri(inlineCs.getUrl()));
    }
    if (StringUtils.isNotEmpty(conceptDisplay)) {
      result.addParameter(new ParametersParameter("display").setValueString(conceptDisplay));
    }
    // A valid but non-active (deprecated/retired/inactive) concept stays result=true, but echoes its `status` and
    // a code-comment warning advising review — the reference server's inactive-concept envelope.
    String status = conceptStatus(concept);
    if (status != null && List.of("deprecated", "retired", "inactive").contains(status)) {
      String note = String.format("The concept '%s' is %s and its use should be reviewed", code, status);
      result.addParameter(new ParametersParameter("status").setValueCode(status));
      result.addParameter(new ParametersParameter("message").setValueString(note));
      result.addParameter(new ParametersParameter("issues").setResource(org.termx.terminology.fhir.TxIssues.outcome(
          org.termx.terminology.fhir.TxIssues.issue("warning", "business-rule", "code-comment", note))));
    } else if (display != null && conceptDisplay != null && !display.equals(conceptDisplay) && designationInactive(concept, display)) {
      // The supplied display is a valid designation but a non-active one (its standards-status is withdrawn/
      // deprecated): result stays true, but a display-comment warning notes it is no longer a correct display and
      // points at the active display.
      String note = String.format("'%s' is no longer considered a correct display for code '%s' (status = deprecated). The correct display is one of \"%s\".",
          display, code, conceptDisplay);
      // A display-comment carries no separate `message` parameter (unlike the code-comment envelope above).
      result.addParameter(new ParametersParameter("issues").setResource(org.termx.terminology.fhir.TxIssues.outcome(
          org.termx.terminology.fhir.TxIssues.issue("warning", "invalid", "display-comment", note))));
    }
    return result;
  }

  /** True when the supplied display matches a designation of the inline concept that carries a non-active standards-status. */
  private static boolean designationInactive(com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept concept, String display) {
    if (concept == null || concept.getDesignation() == null) {
      return false;
    }
    return concept.getDesignation().stream()
        .filter(d -> display.equals(d.getValue()) && d.getExtension() != null)
        .flatMap(d -> d.getExtension().stream())
        .filter(e -> "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status".equals(e.getUrl()))
        .map(com.kodality.zmei.fhir.Extension::getValueCode)
        .anyMatch(v -> List.of("deprecated", "withdrawn", "retired").contains(v));
  }

  /**
   * The non-active status (deprecated/retired/inactive) of an inline CodeSystem concept — from a {@code status}
   * concept property or the {@code structuredefinition-standards-status} extension — or null.
   */
  private static String conceptStatus(com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept concept) {
    if (concept == null) {
      return null;
    }
    if (concept.getProperty() != null) {
      String fromProperty = concept.getProperty().stream()
          .filter(pr -> "status".equals(pr.getCode()))
          .map(com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptProperty::getValueCode)
          .filter(java.util.Objects::nonNull).findFirst().orElse(null);
      if (fromProperty != null) {
        return fromProperty;
      }
    }
    if (concept.getExtension() != null) {
      return concept.getExtension().stream()
          .filter(e -> "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status".equals(e.getUrl()))
          .map(com.kodality.zmei.fhir.Extension::getValueCode)
          .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
    return null;
  }

  /** Depth-first search of the inline concept hierarchy by code. */
  private static com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept findInlineConcept(
      List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept> concepts, String code) {
    if (CollectionUtils.isEmpty(concepts)) {
      return null;
    }
    for (var c : concepts) {
      if (code.equals(c.getCode())) {
        return c;
      }
      var nested = findInlineConcept(c.getConcept(), code);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  /** The valid displays for an inline concept: its display, definition and designations, language-narrowed. */
  private static Set<String> inlineDisplays(com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept c,
                                            String displayLanguage,
                                            com.kodality.zmei.fhir.resource.terminology.CodeSystem cs) {
    LinkedHashSet<String> displays = new LinkedHashSet<>();
    String csLang = cs.getLanguage();
    boolean langOk = displayLanguage == null || csLang == null || csLang.equals(displayLanguage) || csLang.startsWith(displayLanguage);
    if (langOk && StringUtils.isNotEmpty(c.getDisplay())) {
      displays.add(c.getDisplay());
    }
    if (langOk && StringUtils.isNotEmpty(c.getDefinition())) {
      displays.add(c.getDefinition());
    }
    if (c.getDesignation() != null) {
      c.getDesignation().stream()
          .filter(d -> d.getValue() != null)
          .filter(d -> displayLanguage == null || d.getLanguage() == null
              || displayLanguage.equals(d.getLanguage()) || d.getLanguage().startsWith(displayLanguage))
          .forEach(d -> displays.add(d.getValue()));
    }
    return displays;
  }

  private static String extractUseSupplement(Parameters req) {
    return req.getParameter().stream()
        .filter(p -> "useSupplement".equals(p.getName()))
        .map(p -> StringUtils.firstNonBlank(p.getValueCanonical(), p.getValueUri(), p.getValueUrl(), p.getValueString()))
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(java.util.stream.Collectors.joining(","));
  }

  public static Set<String> extractDisplays(Concept c, String displayLanguage) {
    if (CollectionUtils.isEmpty(c.getVersions()) || c.getVersions().getFirst().getDesignations() == null) {
      return Set.of();
    }
    List<Designation> designations = c.getVersions().getFirst().getDesignations();
    LinkedHashSet<String> displays = new LinkedHashSet<>();
    Designation primary = ConceptUtil.getDisplay(designations, displayLanguage, List.of());
    if (primary != null && primary.getName() != null) {
      displays.add(primary.getName());
    }
    designations.stream()
        .filter(d -> d.getName() != null)
        .filter(d -> displayLanguage == null || displayLanguage.equals(d.getLanguage()) || (d.getLanguage() != null && d.getLanguage().startsWith(displayLanguage)))
        .filter(d -> "display".equals(d.getDesignationType()) || "abbreviation".equals(d.getDesignationType()) || "definition".equals(d.getDesignationType()))
        .map(Designation::getName)
        .forEach(displays::add);
    return displays;
  }

}
