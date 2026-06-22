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
    String rawUrl = req.findParameter("url")
        .map(pp -> pp.getValueUrl() != null ? pp.getValueUrl() : pp.getValueUri() != null ? pp.getValueUri() : pp.getValueString())
        .orElse(null);
    // A canonical `url` may carry its version inline as `<url>|<version>` (FHIR & tx-ecosystem). Split it the
    // same way terminology-explorer's CanonicalUrlParser does — the version is everything after the first '|' —
    // and treat that pipe version as the requested valueSetVersion when the parameter isn't separately given.
    String url = rawUrl;
    String pipeVersion = null;
    if (rawUrl != null) {
      int pipe = rawUrl.indexOf('|');
      if (pipe >= 0) {
        url = rawUrl.substring(0, pipe);
        pipeVersion = rawUrl.substring(pipe + 1);
      }
    }
    String version = req.findParameter("valueSetVersion").map(ParametersParameter::getValueString).orElse(pipeVersion);

    // tx-resource / inline: validate against a ValueSet supplied inline (the FHIR validator passes the
    // value set in the request rather than relying on stored content) — either an explicit `valueSet`
    // parameter or a `tx-resource` whose url matches the requested url. This path is unauthenticated-safe
    // (no per-resource read privilege check), which is what the conformance runner exercises as a guest,
    // so it must itself produce the full tx-ecosystem response shape (issues, version).
    Optional<com.kodality.zmei.fhir.resource.terminology.ValueSet> explicit = req.findParameter("valueSet")
        .filter(pp -> pp.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.ValueSet)
        .map(pp -> (com.kodality.zmei.fhir.resource.terminology.ValueSet) pp.getResource());
    com.kodality.zmei.fhir.resource.terminology.ValueSet inlineVs;
    if (explicit.isPresent()) {
      inlineVs = explicit.get();
    } else {
      List<com.kodality.zmei.fhir.resource.terminology.ValueSet> txVs = txResourceValueSets(req, url);
      if (txVs.isEmpty()) {
        inlineVs = null;
      } else if (version != null) {
        // A pinned valueSetVersion must resolve to a tx-resource of exactly that version; an unknown version
        // is a hard 404 ("Unable_to_resolve_value_Set_"), not a silent fall back to another inline version.
        String canonical = url;
        inlineVs = txVs.stream().filter(vs -> version.equals(vs.getVersion())).findFirst()
            .orElseThrow(() -> valueSetNotResolvable(canonical, version));
      } else {
        inlineVs = latestByVersion(txVs);
      }
    }
    if (inlineVs != null) {
      Parameters resp = validateInline(inlineVs, req);
      // FHIR $validate-code echoes the codeableConcept it was given (it isn't reconstructed from the match) —
      // the stored path does this in run(ValueSetVersion, …); the inline path must too.
      req.findParameter("codeableConcept")
          .filter(cc -> resp.findParameter("codeableConcept").isEmpty())
          .ifPresent(resp::addParameter);
      return sorted(resp);
    }

    if (url == null) {
      throw new FhirException(400, IssueType.INVALID, "url parameter required");
    }

    ValueSetVersion vsVersion = version == null ? valueSetVersionService.loadLastVersionByUri(url) :
        valueSetVersionService.query(new ValueSetVersionQueryParams().setValueSetUri(url).setVersion(version)).findFirst().orElse(null);
    if (vsVersion == null) {
      if (version != null) {
        throw valueSetNotResolvable(url, version);
      }
      // An unresolvable value set canonical (not inline, not stored) is a hard 404 not-found per the
      // tx-ecosystem, not a 200 with result=false.
      throw org.termx.terminology.fhir.TxIssues.notFoundException(404,
          String.format("A definition for the value Set '%s' could not be found", url));
    }
    return run(vsVersion, req);
  }

  private record CsConcept(boolean found, String display, boolean inactive, String status) {
  }

  private static final String STANDARDS_STATUS_URL = "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status";
  private static final String VALUESET_DEPRECATED_URL = "http://hl7.org/fhir/StructureDefinition/valueset-deprecated";

  /**
   * True when the value set's own compose marks the matched concept as deprecated — either a
   * {@code valueset-deprecated = true} extension or a {@code standards-status = deprecated} extension on the
   * {@code compose.include.concept} entry. This is a value-set-scoped deprecation (the concept may be perfectly
   * active in its code system), so it yields a warning, not an inactive flag.
   */
  private static boolean vsConceptDeprecated(com.kodality.zmei.fhir.resource.terminology.ValueSet vs, String system, String code) {
    if (vs == null || vs.getCompose() == null || vs.getCompose().getInclude() == null || code == null) {
      return false;
    }
    for (var inc : vs.getCompose().getInclude()) {
      if (inc.getConcept() == null || (system != null && inc.getSystem() != null && !system.equals(inc.getSystem()))) {
        continue;
      }
      for (var c : inc.getConcept()) {
        if (!code.equals(c.getCode())) {
          continue;
        }
        var deprecatedExt = c.getExtensions(VALUESET_DEPRECATED_URL);
        if (deprecatedExt != null && deprecatedExt.anyMatch(e -> "true".equals(e.getValueCode()) || Boolean.TRUE.equals(e.getValueBoolean()))) {
          return true;
        }
        var standardsExt = c.getExtensions(STANDARDS_STATUS_URL);
        if (standardsExt != null && standardsExt.anyMatch(e -> "deprecated".equals(e.getValueCode()))) {
          return true;
        }
      }
    }
    return false;
  }

  /** The tx-resource CodeSystem whose canonical url matches {@code system}. */
  private static com.kodality.zmei.fhir.resource.terminology.CodeSystem txCodeSystemResource(Parameters req, String system) {
    if (req.getParameter() == null || system == null) {
      return null;
    }
    return req.getParameter().stream().filter(p -> "tx-resource".equals(p.getName()))
        .map(ParametersParameter::getResource).filter(r -> r instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem)
        .map(r -> (com.kodality.zmei.fhir.resource.terminology.CodeSystem) r)
        .filter(cs -> system.equals(cs.getUrl())).findFirst().orElse(null);
  }

  private static String statusWord(com.kodality.zmei.fhir.resource.terminology.CodeSystem cs) {
    return cs == null ? null : statusWord(cs.getExperimental(), cs.getStatus(), standardsStatus(cs.getExtensions(STANDARDS_STATUS_URL)));
  }

  private static String statusWord(com.kodality.zmei.fhir.resource.terminology.ValueSet vs) {
    // A value set contributes only its standards-status (deprecated/withdrawn), not draft/experimental.
    if (vs == null) {
      return null;
    }
    String ss = standardsStatus(vs.getExtensions(STANDARDS_STATUS_URL));
    return "withdrawn".equals(ss) || "deprecated".equals(ss) ? ss : null;
  }

  private static String standardsStatus(java.util.stream.Stream<com.kodality.zmei.fhir.Extension> exts) {
    return exts == null ? null : exts.map(com.kodality.zmei.fhir.Extension::getValueCode).filter(java.util.Objects::nonNull).findFirst().orElse(null);
  }

  /** BCP-47-ish language tag match: exact, or one is a region refinement of the other (`de` ~ `de-CH`). */
  private static boolean langTagMatches(String a, String b) {
    return a != null && b != null && (a.equals(b) || a.startsWith(b + "-") || b.startsWith(a + "-"));
  }

  /** The non-active status word ("experimental"/"draft"/"deprecated"/"withdrawn"/"retired") of a resource, or null when active. */
  private static String statusWord(Boolean experimental, String status, String standardsStatus) {
    if ("withdrawn".equals(standardsStatus)) {
      return "withdrawn";
    }
    if ("deprecated".equals(standardsStatus)) {
      return "deprecated";
    }
    if (Boolean.TRUE.equals(experimental)) {
      return "experimental";
    }
    if ("draft".equals(status)) {
      return "draft";
    }
    if ("retired".equals(status)) {
      return "retired";
    }
    return null;
  }

  /** Looks a code up in the tx-resource CodeSystem(s) for {@code system} (recursing nested concepts) — its display + whether it's inactive/retired. */
  private static CsConcept txCsConcept(Parameters req, String system, String code) {
    if (req.getParameter() == null || system == null || code == null) {
      return new CsConcept(false, null, false, null);
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs
          && system.equals(cs.getUrl())) {
        CsConcept found = findConcept(cs.getConcept(), code);
        if (found.found()) {
          return found;
        }
      }
    }
    return new CsConcept(false, null, false, null);
  }

  private static CsConcept findConcept(List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept> concepts, String code) {
    if (concepts == null) {
      return new CsConcept(false, null, false, null);
    }
    for (var c : concepts) {
      if (code.equals(c.getCode())) {
        // A retired/deprecated `status` property, or an `inactive=true` property, marks the concept inactive.
        // The `status` word (retired/deprecated) is surfaced for the validate-code inactive envelope.
        String status = c.getProperty() == null ? null : c.getProperty().stream()
            .filter(pr -> "status".equals(pr.getCode())).map(com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptProperty::getValueCode)
            .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        boolean inactive = ("retired".equals(status) || "deprecated".equals(status))
            || (c.getProperty() != null && c.getProperty().stream().anyMatch(pr ->
                "inactive".equals(pr.getCode()) && Boolean.TRUE.equals(pr.getValueBoolean())));
        return new CsConcept(true, c.getDisplay(), inactive, status);
      }
      CsConcept nested = findConcept(c.getConcept(), code);
      if (nested.found()) {
        return nested;
      }
    }
    return new CsConcept(false, null, false, null);
  }

  /** The tx-resource CodeSystem concept (recursing nested concepts) for {@code system}/{@code code}, or null. */
  private static com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept txCsConceptNode(Parameters req, String system, String code) {
    if (req.getParameter() == null || system == null || code == null) {
      return null;
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs
          && system.equals(cs.getUrl())) {
        var node = findCsConceptNode(cs.getConcept(), code);
        if (node != null) {
          return node;
        }
      }
    }
    return null;
  }

  private static com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept findCsConceptNode(
      List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept> concepts, String code) {
    if (concepts == null) {
      return null;
    }
    for (var c : concepts) {
      if (code.equals(c.getCode())) {
        return c;
      }
      var nested = findCsConceptNode(c.getConcept(), code);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  /** True when the concept has a valid DISPLAY (primary display in the CS language, or a non-definition designation) in {@code lang}. */
  private static boolean conceptHasLangDisplay(com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept node,
      String csLang, String lang) {
    if (node == null || lang == null) {
      return false;
    }
    if (node.getDisplay() != null && csLang != null && langTagMatches(csLang, lang)) {
      return true; // the concept's primary display is in the requested language
    }
    return node.getDesignation() != null && node.getDesignation().stream()
        .filter(d -> d.getValue() != null && d.getLanguage() != null && langTagMatches(d.getLanguage(), lang))
        .anyMatch(d -> d.getUse() == null || !"definition".equals(d.getUse().getCode())); // a definition is not a display
  }

  /** True when {@code value} is a valid display of the concept (primary display, or any display-use designation, any language). */
  private static boolean conceptHasDisplayValue(com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept node, String value) {
    if (node == null || value == null) {
      return false;
    }
    if (value.equals(node.getDisplay())) {
      return true;
    }
    return node.getDesignation() != null && node.getDesignation().stream()
        .filter(d -> value.equals(d.getValue()))
        .anyMatch(d -> d.getUse() == null || !"definition".equals(d.getUse().getCode()));
  }

  /** All ValueSets supplied inline via tx-resource params whose canonical url matches (any version). */
  private static List<com.kodality.zmei.fhir.resource.terminology.ValueSet> txResourceValueSets(Parameters req, String url) {
    if (req.getParameter() == null || url == null) {
      return List.of();
    }
    return req.getParameter().stream()
        .filter(p -> "tx-resource".equals(p.getName()))
        .map(ParametersParameter::getResource)
        .filter(r -> r instanceof com.kodality.zmei.fhir.resource.terminology.ValueSet)
        .map(r -> (com.kodality.zmei.fhir.resource.terminology.ValueSet) r)
        .filter(vs -> url.equals(vs.getUrl()))
        .toList();
  }

  /** Versions of the CodeSystem(s) supplied inline via tx-resource params whose canonical url matches {@code system}. */
  private static List<String> txResourceCodeSystemVersions(Parameters req, String system) {
    if (req.getParameter() == null || system == null) {
      return List.of();
    }
    return req.getParameter().stream()
        .filter(p -> "tx-resource".equals(p.getName()))
        .map(ParametersParameter::getResource)
        .filter(r -> r instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem)
        .map(r -> ((com.kodality.zmei.fhir.resource.terminology.CodeSystem) r))
        .filter(cs -> system.equals(cs.getUrl()))
        .map(com.kodality.zmei.fhir.resource.terminology.CodeSystem::getVersion)
        .filter(java.util.Objects::nonNull).distinct().toList();
  }

  private record VersionResolution(String echoVersion, List<OperationOutcomeIssue> issues, boolean hasError, String message,
                                   String xCausedBy, boolean includeVersionNotFound) {
  }

  /** The {@code compose.include.version} that applies to {@code system}+{@code code} — preferring an include that enumerates the code (mixed value sets). */
  private static String includeVersionFor(com.kodality.zmei.fhir.resource.terminology.ValueSet vs, String system, String code) {
    if (vs.getCompose() == null || vs.getCompose().getInclude() == null) {
      return null;
    }
    com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude fallback = null;
    for (var inc : vs.getCompose().getInclude()) {
      if (system == null || system.equals(inc.getSystem())) {
        boolean listsCode = inc.getConcept() != null && inc.getConcept().stream().anyMatch(c -> code.equals(c.getCode()));
        if (listsCode) {
          return inc.getVersion();
        }
        if (fallback == null) {
          fallback = inc;
        }
      }
    }
    return fallback == null ? null : fallback.getVersion();
  }

  /**
   * Returns a copy of the inline value set with each {@code compose.include.version} for {@code system} that the
   * SQL expand can't resolve literally — a wildcard ({@code 1.x.x}) or a version not among the available code
   * system versions ({@code 1}) — rewritten to the resolved concrete version (when it satisfies the original
   * pattern) or dropped, so value set membership is found by code. Concrete, existing versions are left intact
   * (so a mixed/multi-version value set keeps its per-include pinning). The original value set is untouched.
   */
  private static com.kodality.zmei.fhir.resource.terminology.ValueSet normalizeIncludeVersions(
      com.kodality.zmei.fhir.resource.terminology.ValueSet vs, String system, String concreteVersion, List<String> available) {
    if (vs.getCompose() == null || vs.getCompose().getInclude() == null) {
      return vs;
    }
    boolean needsRewrite = vs.getCompose().getInclude().stream().anyMatch(inc ->
        (system == null || system.equals(inc.getSystem())) && inc.getVersion() != null
            && !available.contains(inc.getVersion()));
    if (!needsRewrite) {
      return vs;
    }
    com.kodality.zmei.fhir.resource.terminology.ValueSet copy = FhirMapper.fromJson(
        FhirMapper.toJson(vs), com.kodality.zmei.fhir.resource.terminology.ValueSet.class);
    for (var inc : copy.getCompose().getInclude()) {
      String v = inc.getVersion();
      if ((system == null || system.equals(inc.getSystem())) && v != null && !available.contains(v)) {
        inc.setVersion(concreteVersion != null && org.termx.terminology.fhir.FhirVersions.versionMatches(v, concreteVersion)
            ? concreteVersion : null);
      }
    }
    return copy;
  }

  /**
   * The {@code systemVersion} parameter value. The tx-ecosystem sends it as a {@code valueCode} (the FHIR
   * datatype is {@code code}), so reading only {@code valueString} silently dropped it — leaving version
   * negotiation with no asserted version and skipping the VALUESET_VALUE_MISMATCH / not-found issues for a
   * plain {@code code} input. Accept code/string/uri/canonical forms.
   */
  private static String systemVersionParam(Parameters req) {
    return req.findParameter("systemVersion").map(p -> p.getValueString() != null ? p.getValueString()
        : p.getValueCode() != null ? p.getValueCode()
        : p.getValueUri() != null ? p.getValueUri() : p.getValueCanonical()).orElse(null);
  }

  /** The {@code <version>} of a {@code system-version}/{@code force-system-version}/{@code check-system-version} param naming {@code system} (its value is {@code system|version}). */
  private static String overrideVersion(Parameters req, String name, String system) {
    if (req.getParameter() == null || system == null) {
      return null;
    }
    String prefix = system + "|";
    return req.getParameter().stream()
        .filter(p -> name.equalsIgnoreCase(p.getName()))
        .map(p -> p.getValueCanonical() != null ? p.getValueCanonical() : p.getValueString())
        .filter(v -> v != null && v.startsWith(prefix))
        .map(v -> v.substring(prefix.length()))
        .findFirst().orElse(null);
  }

  /** Resolves a pattern/concrete version to an actual available code system version (latest wildcard match / exact / latest when unpinned); null when no available version satisfies it. */
  private static String concretize(String resolved, List<String> available) {
    if (resolved == null) {
      return available.stream().max(ValueSetValidateCodeOperation::compareVersions).orElse(null);
    }
    if (org.termx.terminology.fhir.FhirVersions.versionHasWildcards(resolved)) {
      return available.stream().filter(a -> org.termx.terminology.fhir.FhirVersions.versionMatches(resolved, a))
          .max(ValueSetValidateCodeOperation::compareVersions).orElse(null);
    }
    return available.contains(resolved) ? resolved : null;
  }

  private static String versionLocation(Parameters req) {
    if (req.findParameter("codeableConcept").isPresent()) {
      return "CodeableConcept.coding[0].version";
    }
    return req.findParameter("coding").isPresent() ? "Coding.version" : "version";
  }

  private static String systemLocation(Parameters req) {
    if (req.findParameter("codeableConcept").isPresent()) {
      return "CodeableConcept.coding[0].system";
    }
    return req.findParameter("coding").isPresent() ? "Coding.system" : "system";
  }

  /**
   * The "Valid display is …" clause of the invalid-display message, mirroring org.hl7.fhir.core. The valid
   * displays are the member's primary display (tagged with the code system's language) plus its designations
   * (each tagged with its own language). A display with no language matches any requested language. When a
   * {@code displayLanguage} is requested and none of the valid displays are in it, the clause instead names
   * the default (primary) display. Each display carries a {@code (lang)} tag only when its language is known.
   */
  private String validDisplayClause(Parameters req,
      com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains match,
      String displayLanguage) {
    String csLang = Optional.ofNullable(txCodeSystemResource(req, match.getSystem()))
        .map(com.kodality.zmei.fhir.resource.terminology.CodeSystem::getLanguage).orElse(null);
    java.util.LinkedHashMap<String, String> displays = new java.util.LinkedHashMap<>(); // value -> language
    if (match.getDisplay() != null) {
      displays.put(match.getDisplay(), csLang);
    }
    Optional.ofNullable(match.getDesignation()).orElse(List.of()).forEach(d -> {
      if (d.getValue() != null) {
        displays.putIfAbsent(d.getValue(), d.getLanguage());
      }
    });
    List<String> reqLangs = StringUtils.isEmpty(displayLanguage) ? List.of()
        : Arrays.stream(displayLanguage.split(",")).map(String::trim).filter(StringUtils::isNotEmpty).toList();
    List<java.util.Map.Entry<String, String>> kept = displays.entrySet().stream()
        .filter(e -> reqLangs.isEmpty() || e.getValue() == null || reqLangs.stream().anyMatch(rl ->
            e.getValue().equals(rl) || e.getValue().startsWith(rl + "-") || rl.startsWith(e.getValue() + "-")))
        .toList();
    String reqStr = StringUtils.isEmpty(displayLanguage) ? "--" : displayLanguage;
    if (kept.isEmpty()) {
      return String.format("There are no valid display names found for language(s) '%s'. Default display is '%s'",
          reqStr, match.getDisplay());
    }
    String choices = kept.stream()
        .map(e -> "'" + e.getKey() + "'" + (e.getValue() != null ? " (" + e.getValue() + ")" : ""))
        .collect(java.util.stream.Collectors.joining(" or "));
    if (kept.size() == 1) {
      return String.format("Valid display is %s (for the language(s) '%s')", choices, reqStr);
    }
    return String.format("Valid display is one of %d choices: %s (for the language(s) '%s')", kept.size(), choices, reqStr);
  }

  /**
   * Reimplements org.hl7.fhir.core {@code ValueSetValidator.determineVersion}: resolve the effective code
   * system version (force &gt; include &gt; system-version &gt; check-system-version, then a more-detailed coding
   * version refines a wildcard), enforce {@code check-system-version}, and flag the right
   * {@code VALUESET_VALUE_MISMATCH} variant plus {@code UNKNOWN_CODESYSTEM_VERSION} when the coding's version
   * differs/doesn't exist. Operates off the tx-resource CodeSystem versions (the inline expand carries none).
   */
  private VersionResolution resolveVersion(Parameters req, String system, String includeVersion, String codingVersion, List<String> available) {
    List<OperationOutcomeIssue> issues = new ArrayList<>();
    boolean hasError = false;
    String loc = versionLocation(req);

    String resolved = includeVersion;
    String force = overrideVersion(req, "force-system-version", system);
    String check = overrideVersion(req, "check-system-version", system);
    if (force != null) {
      resolved = force;
    } else if (StringUtils.isEmpty(resolved)) {
      String def = overrideVersion(req, "system-version", system);
      resolved = def != null ? def : check;
    }
    if (codingVersion != null && resolved != null && org.termx.terminology.fhir.FhirVersions.isMoreDetailed(resolved, codingVersion)) {
      resolved = codingVersion;
    }

    String csVersion = concretize(resolved, available);
    boolean csExists = csVersion != null;

    // check-system-version: the resolved version must match the checked one, else a version-error.
    if (check != null && csVersion != null && !org.termx.terminology.fhir.FhirVersions.versionMatches(check, csVersion)) {
      String msg = String.format("The version '%s' is not allowed for system '%s': required to be '%s' by a version-check parameter", csVersion, system, check);
      issues.add(org.termx.terminology.fhir.TxIssues.issue("error", "exception", "version-error", msg, loc));
      hasError = true;
    }

    // Mismatch between the coding's version and the resolved code system version. An unknown coding version
    // (UNKNOWN_CODESYSTEM_VERSION, an error) is collected first so it sorts ahead of the mismatch and drives
    // the `message`/`x-caused-by-unknown-system`; the mismatch variant (DEFAULT warning / CHANGED / plain) follows.
    String xCausedBy = null;
    boolean includeVersionNotFound = false;
    // A concrete (non-wildcard) VS-include version that doesn't exist among the code system's available versions is
    // reported as not-found — with the valid-versions list — plus x-caused-by-unknown-system, even though the value
    // set still resolves the code by membership. A wildcard include (1.x.x), an available version, or a forced
    // version is fine. (Ordered before the mismatch issue so the combined message leads with the not-found.)
    if (includeVersion != null && force == null
        && !org.termx.terminology.fhir.FhirVersions.versionHasWildcards(includeVersion)
        && !available.isEmpty() && !available.contains(includeVersion)) {
      issues.add(org.termx.terminology.fhir.TxIssues.issue("error", "not-found", "not-found", String.format(
          "A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: %s",
          system, includeVersion, org.termx.terminology.fhir.TxIssues.presentVersionList(available)), systemLocation(req)));
      hasError = true;
      xCausedBy = system + "|" + includeVersion;
      includeVersionNotFound = true;
    }
    if (codingVersion != null && !(csVersion != null && codingVersion.equals(csVersion))) {
      if (csExists && !available.isEmpty() && !available.contains(codingVersion)) {
        issues.add(org.termx.terminology.fhir.TxIssues.issue("error", "not-found", "not-found", String.format(
            "A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: %s",
            system, codingVersion, org.termx.terminology.fhir.TxIssues.presentVersionList(available)), systemLocation(req)));
        hasError = true;
        xCausedBy = system + "|" + codingVersion;
      }
      if (csExists) {
        if (resolved == null) {
          issues.add(org.termx.terminology.fhir.TxIssues.issue("warning", "invalid", "vs-invalid", String.format(
              "The code system '%s' version '%s' for the versionless include in the ValueSet include is different to the one in the value ('%s')",
              system, csVersion, codingVersion), loc));
        } else if (!resolved.equals(includeVersion)) {
          issues.add(org.termx.terminology.fhir.TxIssues.issue("error", "invalid", "vs-invalid", String.format(
              "The code system '%s' version '%s' resulting from the version '%s' in the ValueSet include is different to the one in the value ('%s')",
              system, resolved, notNull(includeVersion), codingVersion), loc));
          hasError = true;
        } else if (!notNull(includeVersion).equals(codingVersion)) {
          issues.add(org.termx.terminology.fhir.TxIssues.issue("error", "invalid", "vs-invalid", String.format(
              "The code system '%s' version '%s' in the ValueSet include is different to the one in the value ('%s')",
              system, notNull(includeVersion), codingVersion), loc));
          hasError = true;
        }
      } else {
        issues.add(org.termx.terminology.fhir.TxIssues.issue("error", "invalid", "vs-invalid", String.format(
            "The code system '%s' version '%s' in the ValueSet include is different to the one in the value ('%s')",
            system, resolved, codingVersion), loc));
        hasError = true;
      }
    }

    String echo = csVersion != null ? csVersion
        : codingVersion != null && available.contains(codingVersion) ? codingVersion
        : available.stream().max(ValueSetValidateCodeOperation::compareVersions).orElse(null);
    // The `message` concatenates the ERROR issue texts (warnings/info excluded) in issue order,
    // joined with "; " — mirrors org.hl7.fhir.core's combined validation message.
    String message = issues.stream().filter(i -> "error".equals(i.getSeverity()))
        .map(i -> i.getDetails() == null ? null : i.getDetails().getText()).filter(java.util.Objects::nonNull)
        .collect(java.util.stream.Collectors.joining("; "));
    return new VersionResolution(echo, issues, hasError, message.isEmpty() ? null : message, xCausedBy, includeVersionNotFound);
  }

  private static String notNull(String s) {
    return s == null ? "" : s;
  }

  /** Highest-version tx-resource (the latest, by semantic-version order), falling back to the first. */
  private static com.kodality.zmei.fhir.resource.terminology.ValueSet latestByVersion(
      List<com.kodality.zmei.fhir.resource.terminology.ValueSet> vss) {
    return vss.stream().max(java.util.Comparator.comparing(
        com.kodality.zmei.fhir.resource.terminology.ValueSet::getVersion,
        java.util.Comparator.nullsFirst(ValueSetValidateCodeOperation::compareVersions))).orElse(vss.get(0));
  }

  /** Numeric-aware comparison of dotted version strings ("1.10.0" &gt; "1.2.0"), tolerant of non-numeric parts. */
  private static int compareVersions(String a, String b) {
    String[] pa = a.split("\\.");
    String[] pb = b.split("\\.");
    for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
      String sa = i < pa.length ? pa[i] : "0";
      String sb = i < pb.length ? pb[i] : "0";
      int c;
      if (sa.matches("\\d+") && sb.matches("\\d+")) {
        c = Long.compare(Long.parseLong(sa), Long.parseLong(sb));
      } else {
        c = sa.compareTo(sb);
      }
      if (c != 0) {
        return c;
      }
    }
    return 0;
  }

  /**
   * A pinned ValueSet version that resolves to nothing is a 404 OperationOutcome (tx-ecosystem
   * "Unable_to_resolve_value_Set_") — carrying the {@code tx-issue-type} {@code not-found} detail coding the
   * ecosystem matches on, not a bare {@code details.text}.
   */
  static FhirException valueSetNotResolvable(String url, String version) {
    return org.termx.terminology.fhir.TxIssues.notFoundException(404,
        String.format("A definition for the value Set '%s|%s' could not be found", url, version));
  }

  /**
   * A {@code compose.include.valueSet} import naming a value set canonical the server cannot resolve — no bundled
   * tx-resource of that url, and no version was requested — degrades, for {@code $validate-code}, to a 200
   * {@code result=false} with a {@code not-found} issue rather than a hard 404 (the {@code validation}
   * {@code *-bad-import} cases). Returns the first such unresolvable import url, or {@code null} if every import
   * resolves. A ref carrying its own {@code |version} (or pinned by a {@code default-valueset-version} request
   * param) is left to the expand path — a *wrong pinned version* is a different error, and {@code $expand} of an
   * unresolvable import is a hard 4xx, not this graceful 200. Contained ({@code #id}) refs are skipped (resolved
   * elsewhere). Import resolution here matches the expand path's, which is tx-resource-only, so this never
   * diverges from what the expand would have found.
   */
  private static String unresolvableVersionlessImport(com.kodality.zmei.fhir.resource.terminology.ValueSet vs, Parameters req) {
    if (vs.getCompose() == null || vs.getCompose().getInclude() == null) {
      return null;
    }
    for (var inc : vs.getCompose().getInclude()) {
      if (inc.getValueSet() == null) {
        continue;
      }
      for (String ref : inc.getValueSet()) {
        if (ref == null || ref.startsWith("#")) {
          continue;
        }
        int pipe = ref.indexOf('|');
        String refUrl = pipe >= 0 ? ref.substring(0, pipe) : ref;
        String refVersion = pipe >= 0 ? ref.substring(pipe + 1) : importDefaultVersion(req, refUrl);
        if (refVersion != null) {
          continue; // a specific version was requested → expand-path 404, not this graceful degradation
        }
        if (txResourceValueSets(req, refUrl).isEmpty()) {
          return refUrl;
        }
      }
    }
    return null;
  }

  /** The {@code default-valueset-version} request param's version for the given value set url (mirrors the expand path). */
  private static String importDefaultVersion(Parameters req, String vsUrl) {
    if (req == null || req.getParameter() == null) {
      return null;
    }
    return req.getParameter().stream()
        .filter(p -> "default-valueset-version".equals(p.getName()))
        .map(p -> p.getValueCanonical() != null ? p.getValueCanonical() : p.getValueUri() != null ? p.getValueUri() : p.getValueString())
        .filter(java.util.Objects::nonNull)
        .filter(v -> v.startsWith(vsUrl + "|"))
        .map(v -> v.substring(vsUrl.length() + 1))
        .findFirst().orElse(null);
  }

  /**
   * The graceful {@code $validate-code} response for an unresolvable value set import: 200, result=false, and a
   * single {@code not-found} issue at the value set (no location) — mirrors org.hl7.fhir.core, whose validator
   * reports the missing imported value set as a code issue rather than failing the request.
   */
  private Parameters valueSetImportNotFound(String importUrl) {
    String message = String.format("A definition for the value Set '%s' could not be found", importUrl);
    OperationOutcomeIssue notFound = org.termx.terminology.fhir.TxIssues.issue("error", "not-found", "not-found", message);
    Parameters parameters = new Parameters();
    parameters.addParameter(new ParametersParameter("issues").setResource(org.termx.terminology.fhir.TxIssues.outcome(notFound)));
    parameters.addParameter(new ParametersParameter("message").setValueString(message));
    parameters.addParameter(new ParametersParameter("result").setValueBoolean(false));
    return parameters;
  }

  /** Sorts response parameters by name — the tx-ecosystem TxTester diffs parameters positionally against an alphabetically-ordered expected list. */
  private static Parameters sorted(Parameters p) {
    if (p != null && p.getParameter() != null) {
      p.getParameter().sort(java.util.Comparator.comparing(ParametersParameter::getName, java.util.Comparator.nullsLast(String::compareTo)));
    }
    return p;
  }

  /**
   * Validates a code against an inline ValueSet by expanding it (reusing {@link ValueSetExpandOperation})
   * and checking membership in the expansion — the path used when the value set is supplied in the request
   * rather than stored.
   */
  /**
   * Validates a multi-coding CodeableConcept: each coding is validated independently (as a coding input) and the
   * results merged the way org.hl7.fhir.core does — the echoed code/system/display/version come from the LAST
   * VALID coding, result is true only when EVERY coding is valid, and the issues aggregate across codings with
   * their per-coding locations dropped (the reference reports none on a codeableConcept). The codeableConcept
   * itself is echoed by the run() wrapper.
   */
  private Parameters validateCodeableConceptMulti(com.kodality.zmei.fhir.resource.terminology.ValueSet inlineVs,
      Parameters req, List<Coding> codings) {
    List<Parameters> subs = new ArrayList<>();
    for (Coding coding : codings) {
      Parameters subReq = new Parameters();
      Optional.ofNullable(req.getParameter()).orElse(List.of()).stream()
          .filter(p -> !java.util.Set.of("codeableConcept", "coding", "code", "system", "display").contains(p.getName()))
          .forEach(subReq::addParameter);
      subReq.addParameter(new ParametersParameter("coding").setValueCoding(coding));
      subs.add(validateInline(inlineVs, subReq));
    }
    java.util.function.Function<Parameters, Boolean> isValid =
        s -> Boolean.TRUE.equals(s.findParameter("result").map(ParametersParameter::getValueBoolean).orElse(false));
    Parameters winner = subs.get(subs.size() - 1);
    for (Parameters s : subs) {
      if (isValid.apply(s)) {
        winner = s;
      }
    }
    java.util.function.Function<OperationOutcomeIssue, String> txTypeOf = iss ->
        iss.getDetails() != null && iss.getDetails().getCoding() != null
            ? iss.getDetails().getCoding().stream().findFirst().map(com.kodality.zmei.fhir.datatypes.Coding::getCode).orElse(null) : null;
    List<OperationOutcomeIssue> merged = new ArrayList<>();
    for (Parameters s : subs) {
      s.findParameter("issues").map(ParametersParameter::getResource)
          .filter(r -> r instanceof com.kodality.zmei.fhir.resource.other.OperationOutcome)
          .map(r -> ((com.kodality.zmei.fhir.resource.other.OperationOutcome) r).getIssue())
          .ifPresent(list -> Optional.ofNullable(list).orElse(List.of()).forEach(iss -> {
            // A wrong DISPLAY on one coding does not make the codeableConcept invalid (another coding, or the
            // same code under a different display, may be fine) — the reference drops per-coding display issues.
            if ("invalid-display".equals(txTypeOf.apply(iss))) {
              return;
            }
            iss.setLocation(null);
            iss.setExpression(null);
            // A coding that is merely not in the value set is INFORMATIONAL in a CC (another coding may satisfy
            // it) and carries the `this-code-not-in-vs` tx-issue-type; only an unknown/invalid code stays an error.
            if ("not-in-vs".equals(txTypeOf.apply(iss))) {
              iss.setSeverity("information");
              iss.getDetails().getCoding().stream().findFirst().ifPresent(c -> c.setCode("this-code-not-in-vs"));
            }
            merged.add(iss);
          }));
    }
    java.util.Map<String, Integer> severityRank = java.util.Map.of("fatal", 0, "error", 1, "warning", 2, "information", 3);
    merged.sort(java.util.Comparator.comparingInt(i -> severityRank.getOrDefault(i.getSeverity(), 4)));
    // The codeableConcept is valid unless a code-level error survived (an unknown/invalid code or unresolved
    // version). Display-only problems were dropped above, so they don't fail the result.
    boolean valid = merged.stream().noneMatch(i -> "error".equals(i.getSeverity()) || "fatal".equals(i.getSeverity()));
    Parameters resp = new Parameters();
    resp.addParameter(new ParametersParameter("result").setValueBoolean(valid));
    winner.findParameter("code").ifPresent(resp::addParameter);
    winner.findParameter("system").ifPresent(resp::addParameter);
    winner.findParameter("display").ifPresent(resp::addParameter);
    winner.findParameter("version").ifPresent(resp::addParameter);
    if (!merged.isEmpty()) {
      resp.addParameter(new ParametersParameter("issues").setResource(
          org.termx.terminology.fhir.TxIssues.outcome(merged.toArray(OperationOutcomeIssue[]::new))));
      String msg = merged.stream().filter(i -> "error".equals(i.getSeverity()))
          .map(i -> i.getDetails() == null ? null : i.getDetails().getText()).filter(java.util.Objects::nonNull)
          .collect(java.util.stream.Collectors.joining("; "));
      if (!msg.isEmpty()) {
        resp.addParameter(new ParametersParameter("message").setValueString(msg));
      }
    }
    return resp;
  }

  private Parameters validateInline(com.kodality.zmei.fhir.resource.terminology.ValueSet inlineVs, Parameters req) {
    // An import naming a value set the server cannot resolve (no version requested) makes the value set
    // un-assemblable: report it as a not-found code issue (200, result=false), not a 4xx — the reference engine
    // degrades $validate-code this way regardless of the code (the `validation` *-bad-import cases).
    String unresolvableImport = unresolvableVersionlessImport(inlineVs, req);
    if (unresolvableImport != null) {
      return valueSetImportNotFound(unresolvableImport);
    }
    // A CodeableConcept with MORE THAN ONE coding: validate each coding independently and merge. A single-coding
    // codeableConcept (the common case) falls through to the normal path below.
    if (req.findParameter("codeableConcept").isPresent()) {
      CodeableConcept ccMulti = req.findParameter("codeableConcept").map(ParametersParameter::getValueCodeableConcept).orElse(null);
      if (ccMulti != null && ccMulti.getCoding() != null && ccMulti.getCoding().size() > 1) {
        return validateCodeableConceptMulti(inlineVs, req, ccMulti.getCoding());
      }
    }
    String displayLanguage = req.findParameter("displayLanguage").map(p -> p.getValueCode() != null ? p.getValueCode() : p.getValueString()).orElse(null);
    String code = req.findParameter("code").map(p -> p.getValueCode() != null ? p.getValueCode() : p.getValueString()).orElse(null);
    String system = findSystem(req);
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);
    String reqCsVersion = systemVersionParam(req);
    if (req.findParameter("coding").isPresent()) {
      Coding coding = req.findParameter("coding").map(ParametersParameter::getValueCoding).orElse(null);
      code = coding != null && coding.getCode() != null ? coding.getCode() : code;
      system = coding != null && coding.getSystem() != null ? coding.getSystem() : system;
      display = coding != null && coding.getDisplay() != null ? coding.getDisplay() : display;
      reqCsVersion = coding != null && coding.getVersion() != null ? coding.getVersion() : reqCsVersion;
    }
    if (req.findParameter("codeableConcept").isPresent()) {
      CodeableConcept cc = req.findParameter("codeableConcept").map(ParametersParameter::getValueCodeableConcept).orElse(null);
      Coding coding = cc != null && cc.getCoding() != null ? cc.getCoding().stream().findFirst().orElse(null) : null;
      code = coding != null && coding.getCode() != null ? coding.getCode() : code;
      system = coding != null && coding.getSystem() != null ? coding.getSystem() : system;
      reqCsVersion = coding != null && coding.getVersion() != null ? coding.getVersion() : reqCsVersion;
    }
    if (code == null) {
      throw new FhirException(400, IssueType.INVALID, "code, coding or codeableConcept parameter required");
    }

    // The inline SQL expand can't reach external providers, so the resolved code system version isn't on the
    // expansion members — derive it from the tx-resource CodeSystem(s) the validator passed for `system`.
    List<String> availableCsVersions = txResourceCodeSystemVersions(req, system);
    String includeVersion = includeVersionFor(inlineVs, system, code);
    // Version negotiation up front (mirrors org.hl7.fhir.core ValueSetValidator.determineVersion): the resolved
    // concrete code system version drives BOTH which version the value set is expanded at — so a wildcard
    // (1.x.x), overridden, or coding-refined include version still finds the code, since value set membership
    // is by code, not by the literal version string — and the VALUESET_VALUE_MISMATCH /
    // UNKNOWN_CODESYSTEM_VERSION / version-check issues below.
    VersionResolution vr = resolveVersion(req, system, includeVersion, reqCsVersion, availableCsVersions);

    Parameters expandReq = new Parameters();
    expandReq.addParameter(new ParametersParameter("valueSet").setResource(
        normalizeIncludeVersions(inlineVs, system, vr.echoVersion(), availableCsVersions)));
    expandReq.addParameter(new ParametersParameter("includeDesignations").setValueBoolean(true));
    if (displayLanguage != null) {
      expandReq.addParameter(new ParametersParameter("displayLanguage").setValueCode(displayLanguage));
    }
    // Forward the supporting tx-resource resources so the inline expand can resolve imported value sets
    // (compose.include.valueSet, P8) and code-system metadata that were bundled with the request.
    Optional.ofNullable(req.getParameter()).orElse(List.of()).stream()
        .filter(p -> "tx-resource".equals(p.getName()))
        .forEach(expandReq::addParameter);
    com.kodality.zmei.fhir.resource.terminology.ValueSet expanded = expandOperation.run(expandReq);
    String finalCode = code;
    String finalSystem = system;
    var match = Optional.ofNullable(expanded.getExpansion())
        .map(com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion::getContains).orElse(List.of()).stream()
        .filter(c -> finalCode.equals(c.getCode()) && (finalSystem == null || finalSystem.equals(c.getSystem())))
        .findFirst().orElse(null);

    String vsCanonical = inlineVs.getUrl() + (inlineVs.getVersion() != null ? "|" + inlineVs.getVersion() : "");
    // inferSystem with no system supplied: the code's system is inferred from the value set's expansion. If the
    // code appears under MORE THAN ONE code system, the system is ambiguous — the reference engine returns
    // result=false with a `cannot-infer` error (plus the standard not-in-vs), naming the candidate systems,
    // rather than silently picking one. A unique match keeps the normal success path (the `implied` cases).
    boolean inferSystem = req.findParameter("inferSystem")
        .map(p -> Boolean.TRUE.equals(p.getValueBoolean()) || "true".equals(p.getValueString())).orElse(false);
    if (inferSystem && system == null) {
      java.util.List<String> matchSystems = Optional.ofNullable(expanded.getExpansion())
          .map(com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion::getContains).orElse(List.of()).stream()
          .filter(c -> finalCode.equals(c.getCode())).map(c -> c.getSystem())
          .filter(java.util.Objects::nonNull).distinct().sorted().toList();
      if (matchSystems.size() > 1) {
        String notInVs = String.format("The provided code '#%s' was not found in the value set '%s'", code, vsCanonical);
        String cannotInfer = String.format(
            "The System URI could not be determined for the code '%s' in the ValueSet '%s': value set expansion has multiple matches: [%s]",
            code, vsCanonical, String.join(", ", matchSystems));
        Parameters ambiguous = new Parameters();
        ambiguous.addParameter(new ParametersParameter("code").setValueCode(code));
        ambiguous.addParameter(new ParametersParameter("issues").setResource(org.termx.terminology.fhir.TxIssues.outcome(
            org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "not-in-vs", notInVs, "code"),
            org.termx.terminology.fhir.TxIssues.issue("error", "not-found", "cannot-infer", cannotInfer, "code"))));
        ambiguous.addParameter(new ParametersParameter("message").setValueString(cannotInfer + "; " + notInVs));
        ambiguous.addParameter(new ParametersParameter("result").setValueBoolean(false));
        return ambiguous;
      }
    }
    // Case-insensitive code system: a code that fails the exact match but matches a member differing only by case
    // is valid when the code system declares caseSensitive=false. The response echoes the code AS GIVEN plus the
    // normalized (correct-case) code and an information/code-rule note; result stays true.
    if (match == null && finalSystem != null) {
      com.kodality.zmei.fhir.resource.terminology.CodeSystem csRes = txCodeSystemResource(req, finalSystem);
      if (csRes != null && Boolean.FALSE.equals(csRes.getCaseSensitive())) {
        var ciMatch = Optional.ofNullable(expanded.getExpansion())
            .map(com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion::getContains).orElse(List.of()).stream()
            .filter(c -> finalCode.equalsIgnoreCase(c.getCode()) && finalSystem.equals(c.getSystem()))
            .findFirst().orElse(null);
        if (ciMatch != null && !finalCode.equals(ciMatch.getCode())) {
          String csVer = ciMatch.getVersion() != null ? ciMatch.getVersion()
              : vr.echoVersion() != null ? vr.echoVersion() : csRes.getVersion();
          String ciLoc = req.findParameter("codeableConcept").isPresent() ? "CodeableConcept.coding[0].code"
              : req.findParameter("coding").isPresent() ? "Coding.code" : "code";
          String note = String.format(
              "The code '%s' differs from the correct code '%s' by case. Although the code system '%s%s' is case insensitive, implementers are strongly encouraged to use the correct case anyway",
              code, ciMatch.getCode(), finalSystem, csVer != null ? "|" + csVer : "");
          Parameters ci = new Parameters();
          ci.addParameter(new ParametersParameter("code").setValueCode(code));
          if (ciMatch.getDisplay() != null) {
            ci.addParameter(new ParametersParameter("display").setValueString(ciMatch.getDisplay()));
          }
          ci.addParameter(new ParametersParameter("issues").setResource(org.termx.terminology.fhir.TxIssues.outcome(
              org.termx.terminology.fhir.TxIssues.issue("information", "business-rule", "code-rule", note, ciLoc))));
          ci.addParameter(new ParametersParameter("normalized-code").setValueCode(ciMatch.getCode()));
          ci.addParameter(new ParametersParameter("result").setValueBoolean(true));
          ci.addParameter(new ParametersParameter("system").setValueUri(finalSystem));
          if (csVer != null) {
            ci.addParameter(new ParametersParameter("version").setValueString(csVer));
          }
          return ci;
        }
      }
    }
    Parameters resp = new Parameters();
    if (match == null) {
      // Unknown code system: when the code's system has no resolvable definition at all — no member in the
      // expansion, no tx-resource CodeSystem, not among the tx-resource concepts — the reference server degrades
      // gracefully (a 200, not a 4xx) to a not-found issue at `system` + an `x-caused-by-unknown-system`, rather
      // than the not-in-vs/invalid-code shape (which assumes the system is known and just lacks the code).
      boolean systemHasMembers = system != null && Optional.ofNullable(expanded.getExpansion())
          .map(com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion::getContains).orElse(List.of()).stream()
          .anyMatch(c -> finalSystem.equals(c.getSystem()));
      if (!req.findParameter("codeableConcept").isPresent() && system != null && !systemHasMembers
          && txResourceCodeSystemVersions(req, system).isEmpty() && !txCsConcept(req, system, code).found()) {
        // Whether the unknown system is the value set's own include system decides the shape: its own include
        // system → a single not-found (x-caused-by-unknown-system); an unrelated system → also a not-in-vs at
        // `code` and x-unknown-system (errors/unknown-system1 vs unknown-system2).
        boolean systemInIncludes = inlineVs.getCompose() != null && inlineVs.getCompose().getInclude() != null
            && inlineVs.getCompose().getInclude().stream().anyMatch(inc -> finalSystem.equals(inc.getSystem()));
        return unknownSystem(code, system, systemInIncludes, vsCanonical);
      }
      // Code not in the value set: the tx-ecosystem expects a 200 with result=false, the code/system/version
      // echoed, and a structured `issues` OperationOutcome (not-in-vs at the value set + invalid-code at the
      // code system), not a flat message alone. Mirror the stored-content path's shape.
      String csVersion = Optional.ofNullable(expanded.getExpansion())
          .map(com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion::getContains).orElse(List.of()).stream()
          .filter(c -> finalSystem == null || finalSystem.equals(c.getSystem()))
          .map(c -> c.getVersion())
          .filter(java.util.Objects::nonNull).findFirst().orElse(null);
      // The resolved code system version (the message echoes it on the "Unknown code …" issue).
      String csv = vr.echoVersion() != null ? vr.echoVersion() : csVersion;
      // The code reference in the message carries the provided display in parens, as the reference engine does.
      String codeRef = (system != null ? system + "#" : "") + code + (display != null ? " ('" + display + "')" : "");
      String providedNotFound = String.format("The provided code '%s' was not found in the value set '%s'", codeRef, vsCanonical);
      // FHIR $validate-code echoes the input as given: a codeableConcept input echoes `codeableConcept` (added
      // by the run() wrapper) and NOT a decomposed `code`/`system`/`version`. A codeableConcept's primary
      // not-in-vs issue is "No valid coding was found …", with a separate information-level this-code-not-in-vs.
      boolean ccInput = req.findParameter("codeableConcept").isPresent();
      boolean codingInput = req.findParameter("coding").isPresent();
      boolean bareCode = !ccInput && !codingInput;
      String ccLoc = "CodeableConcept.coding[0].code";
      String notInVsText = ccInput ? String.format("No valid coding was found for the value set '%s'", vsCanonical) : providedNotFound;
      // Split: a code that IS defined in the code system but not in the value set echoes its display/version and
      // a single `not-in-vs` issue; a code that the code system does not define adds an `invalid-code` issue.
      CsConcept csConcept = txCsConcept(req, system, code);
      // A code system labeled content=fragment holds only a SUBSET of its codes, so an unknown code is a WARNING
      // (not an error) and validation still succeeds — the code may be valid in another fragment. A single
      // invalid-code warning with the fragment note, no not-in-vs.
      com.kodality.zmei.fhir.resource.terminology.CodeSystem fragCs = system == null ? null : txCodeSystemResource(req, system);
      if (fragCs != null && "fragment".equals(fragCs.getContent()) && !csConcept.found()) {
        String fragVer = vr.echoVersion() != null ? vr.echoVersion() : (csVersion != null ? csVersion : fragCs.getVersion());
        String fragLoc = ccInput ? ccLoc : codingInput ? "Coding.code" : "code";
        String fragText = String.format(
            "Unknown Code '%s' in the CodeSystem '%s'%s - note that the code system is labeled as a fragment, so the code may be valid in some other fragment",
            code, system, fragVer != null ? " version '" + fragVer + "'" : "");
        Parameters frag = new Parameters();
        frag.addParameter(new ParametersParameter("code").setValueCode(code));
        frag.addParameter(new ParametersParameter("issues").setResource(org.termx.terminology.fhir.TxIssues.outcome(
            org.termx.terminology.fhir.TxIssues.issue("warning", "code-invalid", "invalid-code", fragText, fragLoc))));
        frag.addParameter(new ParametersParameter("result").setValueBoolean(true));
        frag.addParameter(new ParametersParameter("system").setValueUri(system));
        if (fragVer != null) {
          frag.addParameter(new ParametersParameter("version").setValueString(fragVer));
        }
        return frag;
      }
      List<OperationOutcomeIssue> nfIssues = new ArrayList<>();
      // Issue locations follow the reference engine: a bare `code` input points at `code`; coding/codeableConcept
      // not-in-vs has no location; an invalid-code on a codeableConcept points at CodeableConcept.coding[0].code.
      nfIssues.add(bareCode
          ? org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "not-in-vs", notInVsText, "code")
          : org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "not-in-vs", notInVsText));
      String message = notInVsText;
      if (!csConcept.found() && system != null) {
        String unknownCode = String.format("Unknown code '%s' in the CodeSystem '%s'%s", code, system, csv != null ? " version '" + csv + "'" : "");
        nfIssues.add(bareCode ? org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "invalid-code", unknownCode, "code")
            : ccInput ? org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "invalid-code", unknownCode, ccLoc)
            : org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "invalid-code", unknownCode));
        message = (ccInput ? providedNotFound : notInVsText) + "; " + unknownCode;
      }
      // A codeableConcept also carries an information-level this-code-not-in-vs issue (the "provided code" text).
      if (ccInput) {
        nfIssues.add(org.termx.terminology.fhir.TxIssues.issue("information", "code-invalid", "this-code-not-in-vs", providedNotFound, ccLoc));
      }
      // An inactive (retired/deprecated) but otherwise-valid code carries a code-rule error (status review) and
      // a code-comment warning (valid but not active), and the response echoes `inactive`.
      boolean inactiveConcept = csConcept.found() && csConcept.inactive();
      if (inactiveConcept) {
        nfIssues.add(0, org.termx.terminology.fhir.TxIssues.issue("error", "business-rule", "code-rule",
            String.format("The concept '%s' is valid but is not active", code)));
        nfIssues.add(org.termx.terminology.fhir.TxIssues.issue("warning", "business-rule", "code-comment",
            String.format("The concept '%s' has a status of inactive and its use should be reviewed", code)));
      }
      if (!ccInput) {
        resp.addParameter(new ParametersParameter("code").setValueCode(code));
        if (csConcept.found() && csConcept.display() != null) {
          resp.addParameter(new ParametersParameter("display").setValueString(csConcept.display()));
        }
        if (inactiveConcept) {
          resp.addParameter(new ParametersParameter("inactive").setValueBoolean(true));
        }
        if (system != null) {
          resp.addParameter(new ParametersParameter("system").setValueUri(system));
        }
        if (csVersion != null) {
          resp.addParameter(new ParametersParameter("version").setValueString(csVersion));
        }
      }
      resp.addParameter(new ParametersParameter("message").setValueString(message));
      resp.addParameter(new ParametersParameter("result").setValueBoolean(false));
      resp.addParameter(new ParametersParameter("issues").setResource(
          org.termx.terminology.fhir.TxIssues.outcome(nfIssues.toArray(OperationOutcomeIssue[]::new))));
      return resp;
    }
    String finalDisplay = display;
    // Display validation, by severity (mirrors the reference engine): a provided display that matches the
    // member's primary display is valid with no issue; one that matches a designation but not the primary
    // (e.g. a different language) is valid with an information-level invalid-display; one that matches nothing
    // is an error (result=false) unless lenient-display-validation is set, when it is a warning (result=true).
    boolean matchesPrimary = finalDisplay == null || finalDisplay.equals(match.getDisplay());
    // A provided display only counts as a valid designation when it is in the REQUESTED language: validating a
    // German display under displayLanguage=en must be an error, not an information-level match against the kept
    // German designation. With no displayLanguage requested, any-language designations count.
    String dl = displayLanguage;
    boolean matchesAny = matchesPrimary
        || Optional.ofNullable(match.getDesignation()).orElse(List.of()).stream()
            .filter(d -> StringUtils.isEmpty(dl) || (d.getLanguage() != null
                && (d.getLanguage().equals(dl) || d.getLanguage().startsWith(dl + "-") || dl.startsWith(d.getLanguage() + "-"))))
            .anyMatch(d -> finalDisplay.equals(d.getValue()));
    boolean lenientDisplay = req.findParameter("lenient-display-validation")
        .map(p -> Boolean.TRUE.equals(p.getValueBoolean()) || "true".equals(p.getValueString())).orElse(false);
    String displaySeverity = matchesPrimary ? null : matchesAny ? "information" : lenientDisplay ? "warning" : "error";
    boolean displayValid = !"error".equals(displaySeverity);

    // displayLanguage requested, but the concept has NO valid display in that language (only e.g. a definition or
    // other-language designations): the reference keeps the concept's primary (default-language) display and judges
    // the provided display against the default language. A provided display that is valid for the default language
    // is accepted with an information-level "no display for <lang>" notice; one that matches nothing stays an error.
    // Guarded to a KNOWN default language that differs from the request — when the request language IS satisfied
    // (the primary or a designation is in it) the branch is skipped, preserving "a foreign-language display is an
    // error when the requested language has its own display".
    String langNoneMessage = null;
    if (StringUtils.isNotEmpty(displayLanguage)) {
      String matchSystem = match.getSystem() != null ? match.getSystem() : system;
      String csResourceLang = Optional.ofNullable(txCodeSystemResource(req, matchSystem))
          .map(com.kodality.zmei.fhir.resource.terminology.CodeSystem::getLanguage).orElse(null);
      // Read the concept's designations from the tx-resource CodeSystem (reliable regardless of whether the
      // expansion echoed designations) and split displayLanguage into its requested tags.
      var csNode = txCsConceptNode(req, matchSystem, match.getCode());
      List<String> reqLangs = Arrays.stream(displayLanguage.split(",")).map(String::trim).filter(StringUtils::isNotEmpty).toList();
      boolean hasRequestedLangDisplay = csResourceLang == null // unknown default language → assume satisfied (no override)
          || csNode == null                                    // concept not in a tx-resource CS → leave existing behavior
          || reqLangs.isEmpty()
          || reqLangs.stream().anyMatch(rl -> langTagMatches(csResourceLang, rl) || conceptHasLangDisplay(csNode, csResourceLang, rl));
      if (!hasRequestedLangDisplay) {
        CsConcept primaryConcept = txCsConcept(req, matchSystem, match.getCode());
        String primaryDisplay = primaryConcept.found() && primaryConcept.display() != null ? primaryConcept.display() : match.getDisplay();
        boolean providedIsValidDisplay = finalDisplay == null
            || finalDisplay.equals(primaryDisplay)
            || conceptHasDisplayValue(csNode, finalDisplay);
        if (providedIsValidDisplay) {
          displaySeverity = "information";
          langNoneMessage = String.format(
              "There are no valid display names found for the code %s#%s for language(s) '%s'. The display is '%s' which is a valid display for the default language",
              matchSystem, match.getCode(), displayLanguage, finalDisplay);
        } else {
          displaySeverity = lenientDisplay ? "warning" : "error";
          langNoneMessage = String.format(
              "Wrong Display Name '%s' for %s#%s. There are no valid display names found for language(s) '%s'. Default display is '%s'",
              finalDisplay, matchSystem, match.getCode(), displayLanguage, primaryDisplay);
        }
        displayValid = !"error".equals(displaySeverity);
        match.setDisplay(primaryDisplay); // the response echoes the primary (default-language) display
      }
    }

    List<OperationOutcomeIssue> issues = new ArrayList<>(vr.issues());
    // A valid but inactive code (retired/deprecated `status`, or `inactive=true`): the result stays true, but the
    // response echoes `inactive=true` (+ a `status` param when a retired/deprecated status is declared) and a
    // code-comment WARNING per status word — a generic "inactive" plus, for a retired/deprecated concept, the
    // specific status — mirroring the reference engine's inactive envelope.
    CsConcept matchConcept = txCsConcept(req, match.getSystem() != null ? match.getSystem() : system, match.getCode());
    boolean matchInactive = matchConcept.found() ? matchConcept.inactive() : Boolean.TRUE.equals(match.getInactive());
    String matchStatus = matchConcept.status();
    List<String> inactiveWarnings = new ArrayList<>();
    if (matchInactive) {
      inactiveWarnings.add(String.format("The concept '%s' has a status of inactive and its use should be reviewed", match.getCode()));
      if (matchStatus != null && !"inactive".equals(matchStatus)) {
        inactiveWarnings.add(String.format("The concept '%s' has a status of %s and its use should be reviewed", match.getCode(), matchStatus));
      }
      String inactiveLoc = req.findParameter("codeableConcept").isPresent() ? "CodeableConcept.coding[0]"
          : req.findParameter("coding").isPresent() ? "Coding" : "code";
      inactiveWarnings.forEach(w -> issues.add(
          org.termx.terminology.fhir.TxIssues.issue("warning", "business-rule", "code-comment", w, inactiveLoc)));
    }
    // Status-check: validating a code from an experimental/draft/deprecated CodeSystem (or a withdrawn/deprecated
    // value set) adds an information-level status-check issue (result is unaffected).
    String statusSystem = match.getSystem() != null ? match.getSystem() : system;
    String csStatus = statusWord(txCodeSystemResource(req, statusSystem));
    if (csStatus != null) {
      String csv = vr.echoVersion() != null ? vr.echoVersion() : match.getVersion();
      issues.add(org.termx.terminology.fhir.TxIssues.issue("information", "business-rule", "status-check",
          String.format("Reference to %s CodeSystem %s%s", csStatus, statusSystem, csv != null ? "|" + csv : "")));
    }
    String vsStatus = statusWord(inlineVs);
    if (vsStatus != null) {
      issues.add(org.termx.terminology.fhir.TxIssues.issue("information", "business-rule", "status-check",
          String.format("Reference to %s ValueSet %s%s", vsStatus, inlineVs.getUrl(), inlineVs.getVersion() != null ? "|" + inlineVs.getVersion() : "")));
    }
    // A concept marked deprecated by the value set's own compose (valueset-deprecated / standards-status=deprecated
    // on the include.concept) is still valid, but its use should be reviewed — emit a code-comment warning.
    if (inlineVs != null && vsConceptDeprecated(inlineVs, match.getSystem() != null ? match.getSystem() : system, match.getCode())) {
      String depLoc = req.findParameter("codeableConcept").isPresent() ? "CodeableConcept.coding[0].code"
          : req.findParameter("coding").isPresent() ? "Coding.code" : "code";
      issues.add(org.termx.terminology.fhir.TxIssues.issue("warning", "business-rule", "code-comment",
          String.format("The presence of the concept '%s' in the system '%s' in the value set %s%s is marked with a status of deprecated and its use should be reviewed",
              match.getCode(), match.getSystem() != null ? match.getSystem() : system, inlineVs.getUrl(),
              inlineVs.getVersion() != null ? "|" + inlineVs.getVersion() : ""),
          depLoc));
    }
    resp.addParameter(new ParametersParameter("result").setValueBoolean(displayValid && !vr.hasError()));
    // A codeableConcept whose code could not be validated against a known code-system version (the VS-include
    // version doesn't exist) echoes only `codeableConcept` (added by the run() wrapper) — NOT a decomposed
    // code/system/version. The reference omits them because the code was never validated against a real version.
    boolean ccUnvalidatable = req.findParameter("codeableConcept").isPresent() && vr.includeVersionNotFound();
    if (!ccUnvalidatable) {
      resp.addParameter(new ParametersParameter("code").setValueCode(match.getCode()));
      if (match.getSystem() != null) {
        resp.addParameter(new ParametersParameter("system").setValueUri(match.getSystem()));
      }
    }
    resp.addParameter(new ParametersParameter("display").setValueString(match.getDisplay()));
    String echoVersion = vr.echoVersion() != null ? vr.echoVersion() : match.getVersion();
    if (echoVersion != null && !ccUnvalidatable) {
      resp.addParameter(new ParametersParameter("version").setValueString(echoVersion));
    }
    if (vr.xCausedBy() != null) {
      resp.addParameter(new ParametersParameter("x-caused-by-unknown-system").setValueCanonical(vr.xCausedBy()));
    }
    if (matchInactive) {
      resp.addParameter(new ParametersParameter("inactive").setValueBoolean(true));
      if (matchStatus != null && !"inactive".equals(matchStatus)) {
        resp.addParameter(new ParametersParameter("status").setValueCode(matchStatus));
      }
    }
    if (displaySeverity != null) {
      // Invalid display: a structured `issues` OperationOutcome (invalid-display at the `display`/`Coding.display`
      // element) at the computed severity, mirroring HL7's "Wrong Display Name … Valid display is …" wording.
      // The display-language "no display in the requested language" branch supplies its own message.
      String message = langNoneMessage != null ? langNoneMessage
          : String.format("Wrong Display Name '%s' for %s#%s. %s",
          display, match.getSystem(), match.getCode(),
          validDisplayClause(req, match, displayLanguage));
      resp.addParameter(new ParametersParameter("message").setValueString(message));
      issues.add(org.termx.terminology.fhir.TxIssues.issue(displaySeverity, "invalid", "invalid-display", message, displayLocation(req)));
    } else if (!inactiveWarnings.isEmpty()) {
      // The inactive code-comment warning(s) become the human message when there is no display issue.
      resp.addParameter(new ParametersParameter("message").setValueString(String.join("; ", inactiveWarnings)));
    } else if (vr.message() != null) {
      resp.addParameter(new ParametersParameter("message").setValueString(vr.message()));
    }
    if (!issues.isEmpty()) {
      // Errors before warnings before information — the tx-ecosystem orders an OperationOutcome's issues by severity.
      java.util.List<String> sev = List.of("fatal", "error", "warning", "information");
      issues.sort(java.util.Comparator.comparingInt(i -> {
        int idx = sev.indexOf(i.getSeverity());
        return idx < 0 ? sev.size() : idx;
      }));
      resp.addParameter(new ParametersParameter("issues").setResource(
          org.termx.terminology.fhir.TxIssues.outcome(issues.toArray(OperationOutcomeIssue[]::new))));
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
    return sorted(response);
  }

  private Parameters doRun(ValueSetVersion vsVersion, Parameters req) {
    SessionStore.require().checkPermitted(vsVersion.getValueSet(), Privilege.VS_READ);
    String code = req.findParameter("code").map(p -> p.getValueCode() != null ? p.getValueCode() : p.getValueString()).orElse(null);
    String system = findSystem(req);
    String version = systemVersionParam(req);
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
          return unknownSystemVersion(req, anyVersion.getConcept().getCode(), findDisplay(anyVersion, display, displayLanguage),
              finalSystem, finalVersion, available);
        }
      }
    }

    if (concept == null) {
      return notInValueSet(finalCode, finalSystem, vsConcepts, codeLocation(req));
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
      // Invalid display: the tx-ecosystem expects a 200 with result=false AND a structured `issues`
      // OperationOutcome (tx-issue-type invalid-display at the `display` element) plus a `message`, not a
      // flat message alone. Message mirrors HL7's "Wrong Display Name … Valid display is …" wording.
      String displayLang = concept.getDisplay() != null ? concept.getDisplay().getLanguage() : null;
      String message = String.format("Wrong Display Name '%s' for %s#%s. Valid display is '%s'%s (for the language(s) '%s')",
          display, concept.getConcept().getCodeSystemUri(), concept.getConcept().getCode(), conceptDisplay,
          displayLang != null ? " (" + displayLang + ")" : "",
          StringUtils.isEmpty(displayLanguage) ? "--" : displayLanguage);
      parameters.addParameter(new ParametersParameter("message").setValueString(message));
      parameters.addParameter(new ParametersParameter("issues").setResource(
          org.termx.terminology.fhir.TxIssues.outcome(
              org.termx.terminology.fhir.TxIssues.issue("error", "invalid", "invalid-display", message, displayLocation(req)))));
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
  /**
   * Graceful degradation for an entirely unknown code system (no version qualifier): a 200 with result=false, a
   * not-found issue at {@code system}, and {@code x-caused-by-unknown-system} carrying the system canonical — the
   * code cannot be validated because the system has no definition. Mirrors org.hl7.fhir.core's unknown-system path.
   */
  private Parameters unknownSystem(String code, String system, boolean systemInIncludes, String vsCanonical) {
    Parameters parameters = new Parameters();
    parameters.addParameter(new ParametersParameter("code").setValueCode(code));
    if (systemInIncludes) {
      // The unknown system IS the value set's own include system: a single not-found at `system` (system url
      // quoted), reported via x-caused-by-unknown-system — the value set simply cannot be expanded.
      String message = String.format("A definition for CodeSystem '%s' could not be found, so the code cannot be validated", system);
      parameters.addParameter(new ParametersParameter("issues").setResource(org.termx.terminology.fhir.TxIssues.outcome(
          org.termx.terminology.fhir.TxIssues.issue("error", "not-found", "not-found", message, "system"))));
      parameters.addParameter(new ParametersParameter("message").setValueString(message));
      parameters.addParameter(new ParametersParameter("result").setValueBoolean(false));
      parameters.addParameter(new ParametersParameter("system").setValueUri(system));
      parameters.addParameter(new ParametersParameter("x-caused-by-unknown-system").setValueCanonical(system));
    } else {
      // The unknown system is NOT one the value set includes: the code is additionally not in the value set, so
      // TWO issues — not-in-vs at `code` plus not-found at `system` (system url UNquoted here) — and the system is
      // reported via x-unknown-system. The message lists the not-found first, then the not-in-vs.
      String notInVs = String.format("The provided code '%s#%s' was not found in the value set '%s'", system, code, vsCanonical);
      String notFound = String.format("A definition for CodeSystem %s could not be found, so the code cannot be validated", system);
      parameters.addParameter(new ParametersParameter("issues").setResource(org.termx.terminology.fhir.TxIssues.outcome(
          org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "not-in-vs", notInVs, "code"),
          org.termx.terminology.fhir.TxIssues.issue("error", "not-found", "not-found", notFound, "system"))));
      parameters.addParameter(new ParametersParameter("message").setValueString(notFound + "; " + notInVs));
      parameters.addParameter(new ParametersParameter("result").setValueBoolean(false));
      parameters.addParameter(new ParametersParameter("system").setValueUri(system));
      parameters.addParameter(new ParametersParameter("x-unknown-system").setValueCanonical(system));
    }
    return parameters;
  }

  private Parameters unknownSystemVersion(Parameters req, String code, String displayName, String system, String requestedVersion,
                                          List<String> availableVersions) {
    String availableVersion = availableVersions.stream().findFirst().orElse(null);
    String systemLoc = systemLocation(req);
    String versionLoc = versionLocation(req);
    String message = String.format(
        "A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: %s",
        system, requestedVersion, org.termx.terminology.fhir.TxIssues.presentVersionList(availableVersions));

    OperationOutcomeIssue notFound = new OperationOutcomeIssue()
        .setSeverity("error").setCode("not-found")
        .setDetails(new CodeableConcept(new Coding("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type", "not-found")).setText(message))
        .setLocation(List.of(systemLoc)).setExpression(List.of(systemLoc));
    OperationOutcomeIssue mismatch = new OperationOutcomeIssue()
        .setSeverity("warning").setCode("invalid")
        .setDetails(new CodeableConcept(new Coding("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type", "vs-invalid")).setText(String.format(
            "The code system '%s' version '%s' for the versionless include in the ValueSet include is different to the one in the value ('%s')",
            system, availableVersion, requestedVersion)))
        .setLocation(List.of(versionLoc)).setExpression(List.of(versionLoc));

    OperationOutcome outcome = new OperationOutcome();
    outcome.setIssue(List.of(notFound, mismatch));

    Parameters parameters = new Parameters();
    parameters.addParameter(new ParametersParameter("code").setValueCode(code));
    if (displayName != null) {
      parameters.addParameter(new ParametersParameter("display").setValueString(displayName));
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

  /**
   * The FHIR expression an OperationOutcome issue about the code points at, which depends on how the code was
   * supplied: a bare {@code code} parameter → {@code code}; a {@code coding} → {@code Coding.code}; a
   * {@code codeableConcept} → {@code CodeableConcept.coding[0].code}. The tx-ecosystem checks this exact path.
   */
  private static String codeLocation(Parameters req) {
    if (req.findParameter("codeableConcept").isPresent()) {
      return "CodeableConcept.coding[0].code";
    }
    return req.findParameter("coding").isPresent() ? "Coding.code" : "code";
  }

  /** The issue expression for a display problem, by input form ({@code display} / {@code Coding.display} / {@code CodeableConcept.coding[0].display}). */
  private static String displayLocation(Parameters req) {
    if (req.findParameter("codeableConcept").isPresent()) {
      return "CodeableConcept.coding[0].display";
    }
    return req.findParameter("coding").isPresent() ? "Coding.display" : "display";
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
  private static Parameters notInValueSet(String code, String system, List<ValueSetVersionConcept> vsConcepts, String codeLocation) {
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
            org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "not-in-vs", message, codeLocation),
            org.termx.terminology.fhir.TxIssues.issue("error", "code-invalid", "invalid-code", message, codeLocation))));
    return result;
  }

}
