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
      return error("valueset version not found");
    }
    return run(vsVersion, req);
  }

  private record CsConcept(boolean found, String display, boolean inactive) {
  }

  private static final String STANDARDS_STATUS_URL = "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status";

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
      return new CsConcept(false, null, false);
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
    return new CsConcept(false, null, false);
  }

  private static CsConcept findConcept(List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept> concepts, String code) {
    if (concepts == null) {
      return new CsConcept(false, null, false);
    }
    for (var c : concepts) {
      if (code.equals(c.getCode())) {
        boolean inactive = c.getProperty() != null && c.getProperty().stream().anyMatch(pr ->
            ("status".equals(pr.getCode()) && ("retired".equals(pr.getValueCode()) || "deprecated".equals(pr.getValueCode())))
                || ("inactive".equals(pr.getCode()) && Boolean.TRUE.equals(pr.getValueBoolean())));
        return new CsConcept(true, c.getDisplay(), inactive);
      }
      CsConcept nested = findConcept(c.getConcept(), code);
      if (nested.found()) {
        return nested;
      }
    }
    return new CsConcept(false, null, false);
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

  private record VersionResolution(String echoVersion, List<OperationOutcomeIssue> issues, boolean hasError, String message, String xCausedBy) {
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
    if (codingVersion != null && !(csVersion != null && codingVersion.equals(csVersion))) {
      if (csExists && !available.isEmpty() && !available.contains(codingVersion)) {
        issues.add(org.termx.terminology.fhir.TxIssues.issue("error", "not-found", "not-found", String.format(
            "A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: %s",
            system, codingVersion, String.join(", ", available)), "system"));
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
    // The `message` echoes the primary (first error) issue text — error before warning.
    String message = issues.stream().filter(i -> "error".equals(i.getSeverity()))
        .map(i -> i.getDetails() == null ? null : i.getDetails().getText()).filter(java.util.Objects::nonNull)
        .findFirst().orElse(null);
    return new VersionResolution(echo, issues, hasError, message, xCausedBy);
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
  private Parameters validateInline(com.kodality.zmei.fhir.resource.terminology.ValueSet inlineVs, Parameters req) {
    String displayLanguage = req.findParameter("displayLanguage").map(p -> p.getValueCode() != null ? p.getValueCode() : p.getValueString()).orElse(null);
    String code = req.findParameter("code").map(p -> p.getValueCode() != null ? p.getValueCode() : p.getValueString()).orElse(null);
    String system = findSystem(req);
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);
    String reqCsVersion = req.findParameter("systemVersion").map(ParametersParameter::getValueString).orElse(null);
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
    com.kodality.zmei.fhir.resource.terminology.ValueSet expanded = expandOperation.run(expandReq);
    String finalCode = code;
    String finalSystem = system;
    var match = Optional.ofNullable(expanded.getExpansion())
        .map(com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion::getContains).orElse(List.of()).stream()
        .filter(c -> finalCode.equals(c.getCode()) && (finalSystem == null || finalSystem.equals(c.getSystem())))
        .findFirst().orElse(null);

    String vsCanonical = inlineVs.getUrl() + (inlineVs.getVersion() != null ? "|" + inlineVs.getVersion() : "");
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
        return unknownSystem(code, system);
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
            String.format("The concept '%s' has a status of inactive and its use should be reviewed", code)));
        nfIssues.add(org.termx.terminology.fhir.TxIssues.issue("warning", "business-rule", "code-comment",
            String.format("The concept '%s' is valid but is not active", code)));
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

    List<OperationOutcomeIssue> issues = new ArrayList<>(vr.issues());
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
    resp.addParameter(new ParametersParameter("result").setValueBoolean(displayValid && !vr.hasError()));
    resp.addParameter(new ParametersParameter("code").setValueCode(match.getCode()));
    if (match.getSystem() != null) {
      resp.addParameter(new ParametersParameter("system").setValueUri(match.getSystem()));
    }
    resp.addParameter(new ParametersParameter("display").setValueString(match.getDisplay()));
    String echoVersion = vr.echoVersion() != null ? vr.echoVersion() : match.getVersion();
    if (echoVersion != null) {
      resp.addParameter(new ParametersParameter("version").setValueString(echoVersion));
    }
    if (vr.xCausedBy() != null) {
      resp.addParameter(new ParametersParameter("x-caused-by-unknown-system").setValueCanonical(vr.xCausedBy()));
    }
    if (displaySeverity != null) {
      // Invalid display: a structured `issues` OperationOutcome (invalid-display at the `display`/`Coding.display`
      // element) at the computed severity, mirroring HL7's "Wrong Display Name … Valid display is …" wording.
      String message = String.format("Wrong Display Name '%s' for %s#%s. Valid display is '%s' (for the language(s) '%s')",
          display, match.getSystem(), match.getCode(), match.getDisplay(),
          StringUtils.isEmpty(displayLanguage) ? "--" : displayLanguage);
      resp.addParameter(new ParametersParameter("message").setValueString(message));
      issues.add(org.termx.terminology.fhir.TxIssues.issue(displaySeverity, "invalid", "invalid-display", message, displayLocation(req)));
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
          return unknownSystemVersion(anyVersion.getConcept().getCode(), findDisplay(anyVersion, display, displayLanguage),
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
  private Parameters unknownSystem(String code, String system) {
    String message = String.format("A definition for CodeSystem '%s' could not be found, so the code cannot be validated", system);
    OperationOutcomeIssue notFound = new OperationOutcomeIssue()
        .setSeverity("error").setCode("not-found")
        .setDetails(new CodeableConcept(new Coding("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type", "not-found")).setText(message))
        .setLocation(List.of("system")).setExpression(List.of("system"));
    OperationOutcome outcome = new OperationOutcome();
    outcome.setIssue(List.of(notFound));
    Parameters parameters = new Parameters();
    parameters.addParameter(new ParametersParameter("code").setValueCode(code));
    parameters.addParameter(new ParametersParameter("issues").setResource(outcome));
    parameters.addParameter(new ParametersParameter("message").setValueString(message));
    parameters.addParameter(new ParametersParameter("result").setValueBoolean(false));
    parameters.addParameter(new ParametersParameter("system").setValueUri(system));
    parameters.addParameter(new ParametersParameter("x-caused-by-unknown-system").setValueCanonical(system));
    return parameters;
  }

  private Parameters unknownSystemVersion(String code, String displayName, String system, String requestedVersion,
                                          List<String> availableVersions) {
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
