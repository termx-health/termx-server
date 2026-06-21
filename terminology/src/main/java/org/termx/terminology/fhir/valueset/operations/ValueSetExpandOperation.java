package org.termx.terminology.fhir.valueset.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import org.termx.terminology.Privilege;
import org.termx.core.auth.SessionStore;
import org.termx.core.ts.ValueSetExternalExpandProvider;
import org.termx.terminology.fhir.valueset.ValueSetFhirMapper;
import org.termx.core.sys.provenance.Provenance;
import org.termx.core.sys.provenance.ProvenanceService;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.terminology.terminology.codesystem.concept.ConceptUtil;
import org.termx.terminology.terminology.valueset.ValueSetService;
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService;
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptRepository;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemQueryParams;
import org.termx.ts.codesystem.CodeSystemVersionReference;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import io.micronaut.core.util.StringUtils;
import org.termx.ts.codesystem.Designation;
import com.kodality.commons.model.QueryResult;
import org.termx.ts.valueset.*;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
@RequiredArgsConstructor
public class ValueSetExpandOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final ValueSetService valueSetService;
  private final ProvenanceService provenanceService;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final ValueSetVersionConceptRepository valueSetVersionConceptRepository;
  private final ValueSetFhirMapper mapper;
  private final List<ValueSetExternalExpandProvider> externalExpandProviders;
  private final CodeSystemService codeSystemService;
  private final ConceptService conceptService;
  private final org.termx.terminology.terminology.codesystem.concept.ConceptSupplementService conceptSupplementService;
  private final org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService codeSystemEntityVersionService;

  // SNOMED CT implicit-ValueSet URL family
  // https://build.fhir.org/ig/HL7/UTG/en/SNOMEDCT.html
  private static final String SNOMED_BASE_URL = "http://snomed.info/sct";
  private static final String SNOMED_CS_ID = "snomed-ct";
  // 900000000000455006 |Reference set (foundation metadata concept)| — anchor for `?fhir_vs=refset`
  private static final String SNOMED_REFSET_FOUNDATION_SCTID = "900000000000455006";

  public String getResourceType() {
    return ResourceType.ValueSet.name();
  }

  public String getOperationName() {
    return "expand";
  }

  public ResourceContent run(ResourceId id, ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    String[] parts = ValueSetFhirMapper.parseCompositeId(id.getResourceId());
    String vsId = parts[0];
    String versionNumber = parts[1];

    ValueSetQueryParams vsParams = new ValueSetQueryParams();
    vsParams.setId(vsId);
    vsParams.setLimit(1);
    vsParams.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.VS_READ));
    ValueSet valueSet = valueSetService.query(vsParams).findFirst()
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "value set not found: " + id.getResourceId()));

    com.kodality.zmei.fhir.resource.terminology.ValueSet resp = expand(valueSet, versionNumber, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    com.kodality.zmei.fhir.resource.terminology.ValueSet resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public com.kodality.zmei.fhir.resource.terminology.ValueSet run(Parameters req) {
    // 1. Try inline valueSet parameter first
    com.kodality.zmei.fhir.resource.terminology.ValueSet inlineVs = req.findParameter("valueSet")
        .filter(pp -> pp.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.ValueSet)
        .map(pp -> (com.kodality.zmei.fhir.resource.terminology.ValueSet) pp.getResource())
        .orElse(null);
    if (inlineVs != null) {
      return expandInline(inlineVs, req);
    }

    // 2. Fall back to url-based lookup (existing logic).
    // `url` is a uri/canonical, so accept valueUri (what the FHIR spec & tx-ecosystem tests send) in
    // addition to valueUrl/valueString — previously a POSTed `url` as valueUri was missed → 400.
    String url = req.findParameter("url")
        .map(pp -> pp.getValueUrl() != null ? pp.getValueUrl()
            : pp.getValueUri() != null ? pp.getValueUri()
            : pp.getValueCanonical() != null ? pp.getValueCanonical()
            : pp.getValueString())
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "parameter 'url' or 'valueSet' required"));
    // Pipe-form canonical `<url>|<version>` (FHIR & tx-ecosystem) — split as terminology-explorer's
    // CanonicalUrlParser does (version = everything after the first '|'). Keep the original `url` for the
    // SNOMED implicit-ValueSet handling below (its own `|edition/version` syntax differs); resolve stored and
    // tx-resource ValueSets by the bare canonical, with the pipe version as the requested version when
    // `valueSetVersion` isn't separately supplied.
    String canonicalUrl = url;
    String pipeVersion = null;
    if (!url.startsWith("http://snomed.info/sct")) {
      int pipe = url.indexOf('|');
      if (pipe >= 0) {
        canonicalUrl = url.substring(0, pipe);
        pipeVersion = url.substring(pipe + 1);
        // A query suffix (e.g. the LOINC/implicit `?fhir_vs`) sits after the pipe-version in
        // `<canonical>|<version>?fhir_vs` — it is not part of the version. Strip it; the full
        // `url` (query intact) still drives the implicit-ValueSet handling below.
        int q = pipeVersion.indexOf('?');
        if (q >= 0) {
          pipeVersion = pipeVersion.substring(0, q);
        }
      }
    }
    String versionNr = req.findParameter("valueSetVersion").map(ParametersParameter::getValueString).orElse(pipeVersion);

    // 3. SNOMED CT implicit-ValueSet URLs (`http://snomed.info/sct[/<edition>/version/<date>]?fhir_vs[=...]`)
    //    are recognised before the stored-VS lookup and delegated to the
    //    SnomedValueSetExpandProvider, which already calls Snowstorm with an
    //    ECL expression.
    com.kodality.zmei.fhir.resource.terminology.ValueSet snomedResp = tryExpandSnomedImplicit(url, req);
    if (snomedResp != null) {
      return snomedResp;
    }

    // 3b. tx-resource: the FHIR validator passes referenced resources inline. When `url` names a ValueSet
    //     supplied as a tx-resource, expand that inline definition instead of looking it up in storage. When
    //     several versions of the same canonical are supplied, a pinned version must resolve to exactly that
    //     one (unknown version → 404); otherwise the latest is used.
    List<com.kodality.zmei.fhir.resource.terminology.ValueSet> txVs = txResourceValueSets(req, canonicalUrl);
    if (!txVs.isEmpty()) {
      com.kodality.zmei.fhir.resource.terminology.ValueSet txResourceVs;
      if (versionNr != null) {
        String canonical = canonicalUrl;
        String pinned = versionNr;
        txResourceVs = txVs.stream().filter(vs -> pinned.equals(vs.getVersion())).findFirst()
            .orElseThrow(() -> org.termx.terminology.fhir.TxIssues.notFoundException(404,
                String.format("A definition for the value Set '%s|%s' could not be found", canonical, pinned)));
      } else {
        txResourceVs = latestByVersion(txVs);
      }
      return expandInline(txResourceVs, req);
    }

    // 4. Stored ValueSet lookup.
    ValueSetQueryParams vsParams = new ValueSetQueryParams();
    vsParams.setUri(canonicalUrl);
    vsParams.setLimit(1);
    vsParams.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.VS_READ));
    ValueSet valueSet = valueSetService.query(vsParams).findFirst().orElse(null);
    if (valueSet != null) {
      return expand(valueSet, versionNr, req);
    }

    // 5. Implicit ValueSet over a stored CodeSystem with the same URI. Lets
    //    clients paginate any CodeSystem they know the canonical of, without
    //    needing a wrapper ValueSet stored on the server. `?fhir_vs` (bare,
    //    no value) is leniently stripped — non-SNOMED `?fhir_vs=...` patterns
    //    are not interpreted and the request will 404 below.
    com.kodality.zmei.fhir.resource.terminology.ValueSet csImplicitResp = tryExpandCodeSystemImplicit(url, versionNr, req);
    if (csImplicitResp != null) {
      return csImplicitResp;
    }

    throw new FhirException(404, IssueType.NOTFOUND, "value set not found: " + url);
  }

  public com.kodality.zmei.fhir.resource.terminology.ValueSet expand(ValueSet vs, String versionNr, Parameters req) {
    ValueSetVersion version;
    if (versionNr != null) {
      ValueSetVersionQueryParams vsvParams = new ValueSetVersionQueryParams();
      vsvParams.setValueSet(vs.getId());
      vsvParams.setVersion(versionNr);
      vsvParams.setLimit(1);
      version = valueSetVersionService.query(vsvParams).findFirst().orElse(null);
    } else {
      version = valueSetVersionService.loadLastVersion(vs.getId());
    }
    if (version == null) {
      throw new FhirException(404, IssueType.NOTFOUND, "value set version not found");
    }

    String requestedLanguage = req == null ? null : req.findParameter("displayLanguage").map(ParametersParameter::getValueCode)
        .orElse(req.findParameter("displayLanguage").map(ParametersParameter::getValueString)
        .orElse(req.findParameter("defaultLanguage").map(ParametersParameter::getValueCode)
        .orElse(req.findParameter("defaultLanguage").map(ParametersParameter::getValueString).orElse(null))));
    // Default the display language: an explicit request wins; otherwise fall back to the value set's own
    // resource language (FHIR ValueSet.language → version.preferredLanguage), and finally to "en". So a
    // localized value set (e.g. language=et with an Estonian supplement) renders its Estonian displays by
    // default instead of always English.
    String vsLanguage = version.getPreferredLanguage();
    String displayLanguage = StringUtils.isNotEmpty(requestedLanguage) ? requestedLanguage
        : StringUtils.isNotEmpty(vsLanguage) ? vsLanguage : "en";
    boolean includeDesignations = req != null && req.findParameter("includeDesignations")
        .map(pr -> pr.getValueBoolean() != null && pr.getValueBoolean() || "true".equals(pr.getValueString()))
        .orElse(false);

    ValueSetSnapshot snapshot = valueSetVersionConceptService.expand(vs.getId(), version.getVersion(), displayLanguage, includeDesignations);
    List<ValueSetVersionConcept> expandedConcepts = snapshot.getExpansion();

    // Layer supplements (e.g. a SNOMED-based supplement's localized designations) onto the expanded
    // members. The external SNOMED expand provider fetches base concepts from Snowstorm but never applies
    // supplements, so they were absent from $expand. Fire only when a language/designations were actually
    // requested, the value set declares its own resource language (its displays are meant to be localized),
    // or a supplement is explicitly named — NOT for a plain English-default expand, so the common path
    // keeps its request-agnostic snapshot without paying for supplement auto-discovery.
    boolean applySupplements = StringUtils.isNotEmpty(requestedLanguage) || StringUtils.isNotEmpty(vsLanguage)
        || includeDesignations || StringUtils.isNotEmpty(extractUseSupplement(req));
    List<org.termx.terminology.terminology.codesystem.concept.ConceptSupplementService.UsedSupplement> usedSupplements = List.of();
    if (applySupplements) {
      usedSupplements = conceptSupplementService.mergeSupplementsIntoExpansion(expandedConcepts, supplementParams(displayLanguage, req));
    }
    // The stored snapshot doesn't carry concept property values; load them on demand when the request asks
    // for properties (FHIR $expand `property`), so contains[].property can be populated. Skipped otherwise.
    if (!designationOrPropertyRequested(req, "property").isEmpty()) {
      decoratePropertyValues(expandedConcepts);
    }
    List<Provenance> provenances = provenanceService.find("ValueSetVersion|" + version.getId());

    // FHIR R5 ValueSet/$expand `filter`: free-text typeahead filter applied to the
    // expansion. Match against concept code and display, case-insensitive substring.
    String filter = req == null ? null : req.findParameter("filter")
        .map(pp -> pp.getValueString() != null ? pp.getValueString() : pp.getValueCode()).orElse(null);
    if (filter != null && !filter.isBlank()) {
      String needle = filter.toLowerCase();
      expandedConcepts = expandedConcepts.stream().filter(c -> {
        String code = c.getConcept() != null ? c.getConcept().getCode() : null;
        String display = c.getDisplay() != null ? c.getDisplay().getName() : null;
        return (code != null && code.toLowerCase().contains(needle))
            || (display != null && display.toLowerCase().contains(needle));
      }).toList();
    }

    // FHIR R5 expansion.total: post-filter, pre-pagination count. Capture HERE.
    // The mapper reads snapshot.conceptsTotal in preference to expansion.size().
    int totalAfterFilter = expandedConcepts.size();

    Integer offset = req == null ? null : req.findParameter("offset").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("offset").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));
    if (offset != null) {
      expandedConcepts = offset >= expandedConcepts.size() ? List.of() : expandedConcepts.subList(offset, expandedConcepts.size());
    }
    Integer count = req == null ? null : req.findParameter("count").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("count").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));
    if (count != null) {
      expandedConcepts = expandedConcepts.stream().limit(count).toList();
    }

    // Propagate filter/offset/count results into the snapshot the mapper reads.
    // The service may return a cached snapshot (see ValueSetVersionConceptService.expand
    // line ~83 — `version.getSnapshot()` is reused when current and unfiltered), so
    // mutating it would poison shared state. Build a copy with the trimmed expansion
    // and the FHIR-correct conceptsTotal (post-filter, pre-paging).
    if (expandedConcepts != snapshot.getExpansion()) {
      snapshot = new ValueSetSnapshot()
          .setId(snapshot.getId())
          .setValueSet(snapshot.getValueSet())
          .setValueSetVersion(snapshot.getValueSetVersion())
          .setConceptsTotal(totalAfterFilter)
          .setExpansion(expandedConcepts)
          .setDependencies(snapshot.getDependencies())
          .setCreatedAt(snapshot.getCreatedAt())
          .setCreatedBy(snapshot.getCreatedBy());
    }

    com.kodality.zmei.fhir.resource.terminology.ValueSet result = mapper.toFhir(vs, version, provenances, snapshot, req);
    appendUsedSupplements(result, usedSupplements);
    return result;
  }

  /**
   * Appends a {@code used-supplement} expansion parameter (resolved {@code url|version}) for each supplement
   * that actually contributed to the expansion — the output counterpart of the {@code useSupplement} request
   * input, which is itself not echoed (see {@code ValueSetFhirMapper.EXPANSION_SELECTION_PARAMETERS}).
   */
  private static void appendUsedSupplements(com.kodality.zmei.fhir.resource.terminology.ValueSet result,
      List<org.termx.terminology.terminology.codesystem.concept.ConceptSupplementService.UsedSupplement> usedSupplements) {
    if (result == null || result.getExpansion() == null || usedSupplements == null || usedSupplements.isEmpty()) {
      return;
    }
    List<com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionParameter> params =
        new java.util.ArrayList<>(result.getExpansion().getParameter() != null ? result.getExpansion().getParameter() : List.of());
    usedSupplements.forEach(s -> params.add(new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionParameter()
        .setName("used-supplement").setValueUri(s.asCanonical())));
    result.getExpansion().setParameter(params);
  }

  /**
   * Enriches members the SQL expand couldn't resolve — those from an external (provider-backed, e.g.
   * SNOMED via Snowstorm) code system come back with no display/designations because the in-DB expand
   * has no access to the provider. Fetches them through the concept providers by code system + code and
   * grafts the display + designations. Only members missing a display are fetched, so a pure-local
   * expansion makes no provider calls.
   */
  private void enrichExternalMembers(List<ValueSetVersionConcept> members, String displayLanguage) {
    java.util.Map<String, List<ValueSetVersionConcept>> byCodeSystem = new java.util.LinkedHashMap<>();
    for (ValueSetVersionConcept m : members) {
      if (m.getConcept() == null || StringUtils.isEmpty(m.getConcept().getCode())) {
        continue;
      }
      if (m.getDisplay() != null && StringUtils.isNotEmpty(m.getDisplay().getName())) {
        continue;
      }
      String csId = resolveCodeSystemId(m.getConcept());
      if (StringUtils.isEmpty(csId)) {
        continue;
      }
      byCodeSystem.computeIfAbsent(csId, key -> new java.util.ArrayList<>()).add(m);
    }
    byCodeSystem.forEach((csId, csMembers) -> {
      List<String> codes = csMembers.stream().map(m -> m.getConcept().getCode()).distinct().toList();
      java.util.Map<String, Concept> byCode = conceptService.query(new ConceptQueryParams()
              .setCodeSystem(csId).setCodes(codes).setDisplayLanguage(displayLanguage).limit(codes.size()))
          .getData().stream().collect(java.util.stream.Collectors.toMap(Concept::getCode, c -> c, (a, b) -> a));
      csMembers.forEach(m -> {
        Concept src = byCode.get(m.getConcept().getCode());
        if (src == null || src.getVersions() == null || src.getVersions().isEmpty()) {
          return;
        }
        List<Designation> designations = java.util.Optional.ofNullable(src.getVersions().get(0).getDesignations()).orElse(List.of());
        Designation display = ConceptUtil.getDisplay(designations, displayLanguage, List.of());
        if (display != null) {
          m.setDisplay(display);
        }
        if (m.getAdditionalDesignations() == null || m.getAdditionalDesignations().isEmpty()) {
          m.setAdditionalDesignations(designations);
        }
      });
    });
  }

  /** Resolves the internal code system id for an expansion member, mapping a canonical url to the stored id when the id is absent (inline/external members); null when it resolves to no stored code system (nothing to enrich from). */
  private String resolveCodeSystemId(ValueSetVersionConcept.ValueSetVersionConceptValue c) {
    if (StringUtils.isNotEmpty(c.getCodeSystem())) {
      java.util.Optional<CodeSystem> loaded = codeSystemService.load(c.getCodeSystem());
      if (loaded != null && loaded.isPresent()) {
        return c.getCodeSystem();
      }
    }
    String uri = StringUtils.isNotEmpty(c.getCodeSystemUri()) ? c.getCodeSystemUri() : c.getCodeSystem();
    if (StringUtils.isEmpty(uri)) {
      return null;
    }
    // Unresolvable code system → no provider to enrich from; skip rather than query with a bogus id.
    var byUri = codeSystemService.query(new CodeSystemQueryParams().setUri(uri).limit(1));
    return byUri == null ? null : byUri.findFirst().map(CodeSystem::getId).orElse(null);
  }

  /** Builds the supplement context for an expansion: auto-load supplements for the requested displayLanguage, plus any explicit {@code useSupplement}. */
  private static ConceptQueryParams supplementParams(String displayLanguage, Parameters req) {
    return new ConceptQueryParams()
        .setIncludeSupplement(true)
        .setDisplayLanguage(displayLanguage)
        .setUseSupplement(extractUseSupplement(req))
        // The supplement's own concepts are loaded through ConceptService, which filters by permitted code
        // systems — a null list matches NOTHING (`code_system in (null)`), so without this the supplement
        // designations never load and the inline expansion silently keeps the base display. Scope it to the
        // caller's CS_READ grants.
        .setPermittedCodeSystems(SessionStore.require().getPermittedResourceIds(Privilege.CS_READ));
  }

  private static String extractUseSupplement(Parameters req) {
    if (req == null || req.getParameter() == null) {
      return null;
    }
    String joined = req.getParameter().stream()
        .filter(p -> "useSupplement".equals(p.getName()))
        .map(p -> {
          String v = p.getValueCanonical() != null ? p.getValueCanonical()
              : p.getValueUri() != null ? p.getValueUri()
              : p.getValueUrl() != null ? p.getValueUrl() : p.getValueString();
          return v;
        })
        .filter(StringUtils::isNotEmpty)
        .distinct()
        .collect(java.util.stream.Collectors.joining(","));
    return StringUtils.isEmpty(joined) ? null : joined;
  }

  /** Finds a ValueSet supplied inline via a tx-resource parameter whose url matches the requested url. */
  private static com.kodality.zmei.fhir.resource.terminology.ValueSet findTxResourceValueSet(Parameters req, String url) {
    return txResourceValueSets(req, url).stream().findFirst().orElse(null);
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

  private static final String STANDARDS_STATUS_URL = "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status";
  private static final String VALUESET_DEPRECATED_URL = "http://hl7.org/fhir/StructureDefinition/valueset-deprecated";

  /** The tx-resource CodeSystem whose canonical url matches {@code system}. */
  private static com.kodality.zmei.fhir.resource.terminology.CodeSystem txCodeSystem(Parameters req, String system) {
    if (req == null || req.getParameter() == null || system == null) {
      return null;
    }
    return req.getParameter().stream().filter(p -> "tx-resource".equals(p.getName()))
        .map(ParametersParameter::getResource).filter(r -> r instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem)
        .map(r -> (com.kodality.zmei.fhir.resource.terminology.CodeSystem) r)
        .filter(cs -> system.equals(cs.getUrl())).findFirst().orElse(null);
  }

  private static String statusWarning(com.kodality.zmei.fhir.resource.terminology.CodeSystem cs) {
    return cs == null ? null : statusWarning(cs.getExperimental(), cs.getStatus(), standardsStatus(cs.getExtensions(STANDARDS_STATUS_URL)));
  }

  private static String statusWarning(com.kodality.zmei.fhir.resource.terminology.ValueSet vs) {
    // A value set contributes only its standards-status (deprecated/withdrawn) — NOT draft/experimental, which
    // are routine for value sets and not warned (only code systems warn on those).
    if (vs == null) {
      return null;
    }
    String ss = standardsStatus(vs.getExtensions(STANDARDS_STATUS_URL));
    return "withdrawn".equals(ss) ? "warning-withdrawn" : "deprecated".equals(ss) ? "warning-deprecated" : null;
  }

  private static String standardsStatus(java.util.stream.Stream<com.kodality.zmei.fhir.Extension> exts) {
    return exts == null ? null : exts.map(com.kodality.zmei.fhir.Extension::getValueCode).filter(java.util.Objects::nonNull).findFirst().orElse(null);
  }

  /** The expansion `warning-<status>` parameter name for a non-active resource (withdrawn/deprecated standards-status, experimental, draft, retired), or null. */
  private static String statusWarning(Boolean experimental, String status, String standardsStatus) {
    if ("withdrawn".equals(standardsStatus)) {
      return "warning-withdrawn";
    }
    if ("deprecated".equals(standardsStatus)) {
      return "warning-deprecated";
    }
    if (Boolean.TRUE.equals(experimental)) {
      return "warning-experimental";
    }
    if ("draft".equals(status)) {
      return "warning-draft";
    }
    if ("retired".equals(status)) {
      return "warning-retired";
    }
    return null;
  }

  /** {@code system|code} of concepts the tx-resource CodeSystems mark inactive (property {@code inactive=true} or {@code status} of retired/deprecated/inactive). */
  private static java.util.Set<String> txInactiveCodes(Parameters req) {
    java.util.Set<String> codes = new java.util.HashSet<>();
    if (req == null || req.getParameter() == null) {
      return codes;
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs && cs.getUrl() != null) {
        collectInactive(cs.getConcept(), cs.getUrl(), codes);
      }
    }
    return codes;
  }

  /** {@code system|code} of concepts the tx-resource CodeSystems mark not-selectable (property {@code notSelectable=true}). */
  private static java.util.Set<String> txAbstractCodes(Parameters req) {
    java.util.Set<String> codes = new java.util.HashSet<>();
    if (req == null || req.getParameter() == null) {
      return codes;
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs && cs.getUrl() != null) {
        collectAbstract(cs.getConcept(), cs.getUrl(), codes);
      }
    }
    return codes;
  }

  private static void collectAbstract(List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept> concepts, String system, java.util.Set<String> codes) {
    if (concepts == null) {
      return;
    }
    for (var c : concepts) {
      if (c.getCode() != null && c.getProperty() != null && c.getProperty().stream()
          .anyMatch(pr -> ("notSelectable".equals(pr.getCode()) || "not-selectable".equals(pr.getCode())) && Boolean.TRUE.equals(pr.getValueBoolean()))) {
        codes.add(system + "|" + c.getCode());
      }
      collectAbstract(c.getConcept(), system, codes);
    }
  }

  private static void collectInactive(List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept> concepts, String system, java.util.Set<String> codes) {
    if (concepts == null) {
      return;
    }
    for (var c : concepts) {
      if (c.getCode() != null && c.getProperty() != null && c.getProperty().stream().anyMatch(pr ->
          ("inactive".equals(pr.getCode()) && Boolean.TRUE.equals(pr.getValueBoolean()))
              || ("status".equals(pr.getCode()) && List.of("retired", "deprecated", "inactive").contains(String.valueOf(pr.getValueCode()))))) {
        codes.add(system + "|" + c.getCode());
      }
      collectInactive(c.getConcept(), system, codes);
    }
  }

  /** Canonical urls of tx-resource CodeSystems that declare no version (so a used-codesystem should be the bare system uri). */
  private static java.util.Set<String> versionlessTxCodeSystems(Parameters req) {
    java.util.Set<String> systems = new java.util.HashSet<>();
    if (req == null || req.getParameter() == null) {
      return systems;
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs
          && cs.getUrl() != null && StringUtils.isEmpty(cs.getVersion())) {
        systems.add(cs.getUrl());
      }
    }
    return systems;
  }

  /** Versions of the CodeSystem(s) supplied inline via tx-resource params whose canonical url matches {@code system}. */
  private static List<String> txResourceCodeSystemVersions(Parameters req, String system) {
    if (req == null || req.getParameter() == null || system == null) {
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

  /** The {@code <version>} of a {@code system-version}/{@code force-system-version}/{@code check-system-version} param naming {@code system}. */
  private static String overrideVersion(Parameters req, String name, String system) {
    if (req == null || req.getParameter() == null || system == null) {
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

  /**
   * Returns a copy of the inline value set with each {@code compose.include.version} resolved to a concrete
   * available code system version: {@code force-system-version} overrides; else the include version; else
   * {@code system-version}; else {@code check-system-version}; a wildcard ({@code 1.x.x}) resolves to the
   * highest matching available version. A pinned version that resolves to nothing, or a resolved version that
   * fails {@code check-system-version}, is a 4xx — the expansion can't be produced. The original is untouched.
   */
  private com.kodality.zmei.fhir.resource.terminology.ValueSet resolveIncludeVersions(
      com.kodality.zmei.fhir.resource.terminology.ValueSet inlineVs, Parameters req) {
    if (inlineVs.getCompose() == null || inlineVs.getCompose().getInclude() == null) {
      return inlineVs;
    }
    com.kodality.zmei.fhir.resource.terminology.ValueSet copy = FhirMapper.fromJson(
        FhirMapper.toJson(inlineVs), com.kodality.zmei.fhir.resource.terminology.ValueSet.class);
    for (var inc : copy.getCompose().getInclude()) {
      String system = inc.getSystem();
      if (system == null) {
        continue;
      }
      List<String> available = txResourceCodeSystemVersions(req, system);
      String force = overrideVersion(req, "force-system-version", system);
      String check = overrideVersion(req, "check-system-version", system);
      String resolved = inc.getVersion();
      if (force != null) {
        resolved = force;
      } else if (StringUtils.isEmpty(resolved)) {
        String def = overrideVersion(req, "system-version", system);
        resolved = def != null ? def : check;
      }
      if (resolved == null) {
        continue; // versionless — let the SQL expand the latest
      }
      String pattern = resolved;
      String concrete = org.termx.terminology.fhir.FhirVersions.versionHasWildcards(pattern)
          ? available.stream().filter(a -> org.termx.terminology.fhir.FhirVersions.versionMatches(pattern, a))
              .max(ValueSetExpandOperation::compareVersions).orElse(null)
          : available.contains(pattern) ? pattern : null;
      if (concrete == null) {
        if (!available.isEmpty()) {
          throw org.termx.terminology.fhir.TxIssues.notFoundException(404, String.format(
              "A definition for CodeSystem '%s' version '%s' could not be found, so the value set cannot be expanded. Valid versions: %s",
              system, resolved, org.termx.terminology.fhir.TxIssues.presentVersionList(available)));
        }
        continue;
      }
      if (check != null && !org.termx.terminology.fhir.FhirVersions.versionMatches(check, concrete)) {
        throw org.termx.terminology.fhir.TxIssues.versionCheckException(400, String.format(
            "The version '%s' is not allowed for system '%s': required to be '%s' by a version-check parameter", concrete, system, check));
      }
      inc.setVersion(concrete);
    }
    return copy;
  }

  /**
   * P8 — resolve {@code compose.include[].valueSet} (imported value sets). The SQL expand only handles
   * {@code system}/{@code concept}/{@code filter}; an imported value set is expanded here (recursively, from the
   * bundled tx-resources) and its members rewritten into the include as {@code system}+{@code concept} entries,
   * so the rest of the pipeline (SQL expand, flags, display) handles them. A referenced value set that cannot be
   * resolved — e.g. a wrong pinned {@code url|version} — is a 4xx not-found. {@code visited} guards import cycles.
   */
  private com.kodality.zmei.fhir.resource.terminology.ValueSet resolveImportedValueSets(
      com.kodality.zmei.fhir.resource.terminology.ValueSet inlineVs, Parameters req, java.util.Set<String> visited,
      List<String> usedValueSets) {
    if (inlineVs.getCompose() == null || inlineVs.getCompose().getInclude() == null
        || inlineVs.getCompose().getInclude().stream().noneMatch(i -> (i.getValueSet() != null && !i.getValueSet().isEmpty()))) {
      return inlineVs;
    }
    com.kodality.zmei.fhir.resource.terminology.ValueSet copy = FhirMapper.fromJson(
        FhirMapper.toJson(inlineVs), com.kodality.zmei.fhir.resource.terminology.ValueSet.class);
    List<com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude> newIncludes = new java.util.ArrayList<>();
    for (var inc : copy.getCompose().getInclude()) {
      if ((inc.getValueSet() == null || inc.getValueSet().isEmpty())) {
        newIncludes.add(inc);
        continue;
      }
      // Members of the imported value set(s), grouped by code system uri (preserving order).
      java.util.LinkedHashMap<String, java.util.LinkedHashSet<String>> bySystem = new java.util.LinkedHashMap<>();
      for (String ref : inc.getValueSet()) {
        int pipe = ref.indexOf('|');
        String refUrl = pipe >= 0 ? ref.substring(0, pipe) : ref;
        // The import ref's own pinned version wins; otherwise a `default-valueset-version` request param for this
        // url pins it (a wrong/absent version then resolves to nothing → 4xx below).
        String refVersion = pipe >= 0 ? ref.substring(pipe + 1) : defaultValueSetVersion(req, refUrl);
        com.kodality.zmei.fhir.resource.terminology.ValueSet imported = txResourceValueSets(req, refUrl).stream()
            .filter(vs -> refVersion == null || refVersion.equals(vs.getVersion()))
            .max(java.util.Comparator.comparing(com.kodality.zmei.fhir.resource.terminology.ValueSet::getVersion,
                java.util.Comparator.nullsFirst(ValueSetExpandOperation::compareVersions)))
            .orElse(null);
        if (imported == null) {
          throw org.termx.terminology.fhir.TxIssues.notFoundException(404,
              "Unable to resolve the value set import " + refUrl + (refVersion != null ? "|" + refVersion : ""));
        }
        // Report the resolved imported value set as a `used-valueset` expansion parameter (url|version).
        usedValueSets.add(refUrl + (imported.getVersion() != null ? "|" + imported.getVersion() : ""));
        if (!visited.add(refUrl + "|" + (imported.getVersion() == null ? "" : imported.getVersion()))) {
          continue; // already expanded on this path — guard against import cycles
        }
        com.kodality.zmei.fhir.resource.terminology.ValueSet resolved =
            resolveIncludeVersions(resolveImportedValueSets(imported, req, visited, usedValueSets), req);
        for (ValueSetVersionConcept m : valueSetVersionConceptRepository.expandFromJson(FhirMapper.toJson(resolved))) {
          if (m.getConcept() == null || m.getConcept().getCode() == null) {
            continue;
          }
          String sys = m.getConcept().getCodeSystemUri() != null ? m.getConcept().getCodeSystemUri() : m.getConcept().getBaseCodeSystemUri();
          if (sys != null) {
            bySystem.computeIfAbsent(sys, k -> new java.util.LinkedHashSet<>()).add(m.getConcept().getCode());
          }
        }
      }
      // A pure-import include (only valueSet) is replaced by the imported members; a mixed include keeps its own
      // system/concept/filter (union semantics — both contribute members).
      boolean pureImport = inc.getSystem() == null && (inc.getConcept() == null || inc.getConcept().isEmpty()) && (inc.getFilter() == null || inc.getFilter().isEmpty());
      if (!pureImport) {
        newIncludes.add(inc);
      }
      bySystem.forEach((sys, codes) -> {
        var imp = new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude();
        imp.setSystem(sys);
        imp.setConcept(codes.stream()
            .map(c -> new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConcept().setCode(c)).toList());
        newIncludes.add(imp);
      });
    }
    copy.getCompose().setInclude(newIncludes);
    return copy;
  }

  /**
   * Rejects a value set that cannot be processed because a {@code compose.include}/{@code exclude} filter is
   * missing its {@code value} (1..1 in R5) — a 4xx {@code vs-invalid} carrying the offending filter's location,
   * the way org.hl7.fhir.core does (tx-ecosystem {@code errors/broken-filter}). Only the operation target is
   * checked; a malformed bundled tx-resource is tolerated on the input path.
   */
  private void requireValidFilters(com.kodality.zmei.fhir.resource.terminology.ValueSet vs) {
    if (vs == null || vs.getCompose() == null) {
      return;
    }
    requireValidFilters(vs.getCompose().getInclude(), "include");
    requireValidFilters(vs.getCompose().getExclude(), "exclude");
  }

  private void requireValidFilters(
      List<com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude> includes, String kind) {
    if (includes == null) {
      return;
    }
    for (int i = 0; i < includes.size(); i++) {
      var inc = includes.get(i);
      if (inc.getFilter() == null) {
        continue;
      }
      for (int j = 0; j < inc.getFilter().size(); j++) {
        var f = inc.getFilter().get(j);
        if (StringUtils.isEmpty(f.getValue())) {
          throw org.termx.terminology.fhir.TxIssues.vsInvalidException(400,
              String.format("The system %s filter with property = %s, op = %s has no value", inc.getSystem(), f.getProperty(), f.getOp()),
              String.format("ValueSet.compose.%s[%d].filter[%d]", kind, i, j));
        }
      }
    }
  }

  /**
   * A value set that declares a REQUIRED supplement via the {@code valueset-supplement} extension must have it
   * resolvable — a bundled tx-resource CodeSystem or a stored one with that canonical url. An unresolvable
   * required supplement is a 404 not-found (tx-ecosystem {@code extensions} bad-supplement cases).
   */
  private void requireDeclaredSupplements(com.kodality.zmei.fhir.resource.terminology.ValueSet vs, Parameters req) {
    if (vs == null || vs.getExtension() == null) {
      return;
    }
    for (com.kodality.zmei.fhir.Extension ext : vs.getExtension()) {
      if (!"http://hl7.org/fhir/StructureDefinition/valueset-supplement".equals(ext.getUrl())) {
        continue;
      }
      String ref = ext.getValueCanonical() != null ? ext.getValueCanonical()
          : ext.getValueUri() != null ? ext.getValueUri() : ext.getValueUrl();
      if (StringUtils.isEmpty(ref)) {
        continue;
      }
      String url = ref.contains("|") ? ref.substring(0, ref.indexOf('|')) : ref;
      boolean txPresent = req != null && req.getParameter() != null && req.getParameter().stream()
          .filter(p -> "tx-resource".equals(p.getName()))
          .map(ParametersParameter::getResource)
          .filter(r -> r instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem)
          .map(r -> ((com.kodality.zmei.fhir.resource.terminology.CodeSystem) r).getUrl())
          .anyMatch(url::equals);
      boolean storedPresent = !txPresent && codeSystemService.query(
          new org.termx.ts.codesystem.CodeSystemQueryParams().setUri(url).limit(1)).findFirst().isPresent();
      if (!txPresent && !storedPresent) {
        throw org.termx.terminology.fhir.TxIssues.notFoundException(404, "Required supplement not found: " + ref);
      }
    }
  }

  /** The version pinned for an imported value set by a {@code default-valueset-version} request param
   *  ({@code <vsUrl>|<version>}), or null. */
  private static String defaultValueSetVersion(Parameters req, String vsUrl) {
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

  /** Highest-version tx-resource (latest, by numeric-aware dotted-version order), falling back to the first. */
  private static com.kodality.zmei.fhir.resource.terminology.ValueSet latestByVersion(
      List<com.kodality.zmei.fhir.resource.terminology.ValueSet> vss) {
    return vss.stream().max(java.util.Comparator.comparing(
        com.kodality.zmei.fhir.resource.terminology.ValueSet::getVersion,
        java.util.Comparator.nullsFirst(ValueSetExpandOperation::compareVersions))).orElse(vss.get(0));
  }

  /** Numeric-aware comparison of dotted version strings ("1.10.0" &gt; "1.2.0"), tolerant of non-numeric parts. */
  private static int compareVersions(String a, String b) {
    String[] pa = a.split("\\.");
    String[] pb = b.split("\\.");
    for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
      String sa = i < pa.length ? pa[i] : "0";
      String sb = i < pb.length ? pb[i] : "0";
      int c = sa.matches("\\d+") && sb.matches("\\d+") ? Long.compare(Long.parseLong(sa), Long.parseLong(sb)) : sa.compareTo(sb);
      if (c != 0) {
        return c;
      }
    }
    return 0;
  }

  private com.kodality.zmei.fhir.resource.terminology.ValueSet expandInline(
      com.kodality.zmei.fhir.resource.terminology.ValueSet inlineVs, Parameters req) {
    // Resolve each compose.include.version against the system-version/force-system-version/check-system-version
    // params and wildcard semantics (mirrors org.hl7.fhir.core ValueSetValidator.determineVersion), rewriting it
    // to a concrete available version so the SQL expand picks the right code system version — driving
    // expansion.total and the used-codesystem parameter. A pinned version that resolves to nothing, or a
    // check-system-version mismatch, is a 4xx (the expansion can't be produced).
    // The value set being operated on must be structurally valid: a compose filter is missing its `value`
    // (1..1 in R5) cannot be processed — reject with a 4xx vs-invalid, the way the reference engine does
    // (errors/broken-filter). This is the OPERATION TARGET only; a malformed *supporting* tx-resource is
    // tolerated on the input path (see ProfileTolerantResourceValidator / #283).
    requireValidFilters(inlineVs);
    // A value set that REQUIRES a supplement (valueset-supplement extension) must have it resolvable, else 4xx.
    requireDeclaredSupplements(inlineVs, req);

    // P8: flatten any compose.include[].valueSet (imported value sets) into system+concept includes first.
    List<String> usedValueSets = new java.util.ArrayList<>();
    inlineVs = resolveImportedValueSets(inlineVs, req, new java.util.HashSet<>(), usedValueSets);
    inlineVs = resolveIncludeVersions(inlineVs, req);

    // Serialize the inline ValueSet to JSON
    String valueSetJson = FhirMapper.toJson(inlineVs);

    // Call the JSON-based expand SQL function
    List<ValueSetVersionConcept> expandedConcepts = valueSetVersionConceptRepository.expandFromJson(valueSetJson);

    // Extract parameters
    String displayLanguage = req == null ? null : req.findParameter("displayLanguage").map(ParametersParameter::getValueCode)
        .orElse(req.findParameter("displayLanguage").map(ParametersParameter::getValueString)
        .orElse(req.findParameter("defaultLanguage").map(ParametersParameter::getValueCode)
        .orElse(req.findParameter("defaultLanguage").map(ParametersParameter::getValueString).orElse(null))));
    boolean includeDesignations = req != null && req.findParameter("includeDesignations")
        .map(pr -> pr.getValueBoolean() != null && pr.getValueBoolean() || "true".equals(pr.getValueString()))
        .orElse(false);
    // FHIR $expand `designation` filter tokens (same semantics as the stored path) — restrict which
    // designations the inline expansion returns. Empty = return all.
    List<String> designationFilter = ValueSetFhirMapper.designationFilterTokens(req);
    // FHIR $expand `property` tokens — which concept properties to surface in contains[].property. The
    // decorateExpansionFlags step below loads the members' property values, so they are available here.
    List<String> requestedProperties = designationOrPropertyRequested(req, "property");

    // The SQL expand can't reach external providers (e.g. SNOMED via Snowstorm), so those members arrive
    // bare. Enrich them with display/designations from the providers, then layer supplements onto the
    // whole expansion — so a SNOMED-based supplement surfaces in an inline/tx-resource $expand too.
    enrichExternalMembers(expandedConcepts, displayLanguage);
    List<org.termx.terminology.terminology.codesystem.concept.ConceptSupplementService.UsedSupplement> usedSupplements =
        conceptSupplementService.mergeSupplementsIntoExpansion(expandedConcepts, supplementParams(displayLanguage, req));

    // Apply FHIR R5 `filter` (free-text typeahead) before pagination, matching
    // the stored-VS path semantics. Filters affect `expansion.total`;
    // pagination does not.
    String textFilter = req == null ? null : req.findParameter("filter")
        .map(pp -> pp.getValueString() != null ? pp.getValueString()
            : pp.getValueCode() != null ? pp.getValueCode() : pp.getValueUri()).orElse(null);
    if (textFilter != null && !textFilter.isBlank()) {
      String needle = textFilter.toLowerCase();
      expandedConcepts = expandedConcepts.stream().filter(c -> {
        String code = c.getConcept() != null ? c.getConcept().getCode() : null;
        String display = c.getDisplay() != null ? c.getDisplay().getName() : null;
        return (code != null && code.toLowerCase().contains(needle))
            || (display != null && display.toLowerCase().contains(needle));
      }).toList();
    }

    // Exclude inactive (retired/deprecated) members when asked — either the `activeOnly` request parameter,
    // or the value set's own `compose.inactive = false` (FHIR: inactive codes are excluded unless that flag
    // is absent/true). This changes the set, so it runs before expansion.total. The SQL expand carries no
    // status, so the filter needs the decorated version status — decorate the whole (already in-memory) set
    // up front in this case; the common path decorates only the page below, keeping it page-bounded.
    boolean activeOnlyParam = req != null && req.findParameter("activeOnly")
        .map(pp -> Boolean.TRUE.equals(pp.getValueBoolean()) || "true".equals(pp.getValueString())).orElse(false);
    boolean composeExcludesInactive = inlineVs.getCompose() != null && Boolean.FALSE.equals(inlineVs.getCompose().getInactive());
    boolean excludeInactive = activeOnlyParam || composeExcludesInactive;
    // The SQL expand carries no status/properties, and the DB decoration is unreliable for tx-resource-only
    // code systems — so also derive inactivity straight from the tx-resource CodeSystem's concept properties
    // (`inactive=true` / `status=retired|deprecated|inactive`).
    java.util.Set<String> txInactiveCodes = txInactiveCodes(req);
    java.util.Set<String> txAbstractCodes = txAbstractCodes(req);
    if (excludeInactive) {
      decorateExpansionFlags(expandedConcepts);
      expandedConcepts = expandedConcepts.stream()
          .filter(c -> !isInactiveMember(c) && !(c.getConcept() != null && txInactiveCodes.contains(c.getConcept().getCodeSystemUri() + "|" + c.getConcept().getCode())))
          .toList();
    }

    // FHIR R5 ValueSet.expansion.total: "If the number of codes in an expansion
    // is changed by the parameters supplied, then this should be the count of
    // codes corresponding to the parameters." Filters change the set; offset
    // and count only window it. Capture total HERE — after filter, before
    // pagination — so the response reflects the full set size and clients can
    // paginate without re-issuing a `count=0` discovery probe.
    int totalAfterFilter = expandedConcepts.size();

    // Apply paging
    Integer offset = req == null ? null : req.findParameter("offset").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("offset").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));
    if (offset != null) {
      expandedConcepts = offset >= expandedConcepts.size() ? List.of() : expandedConcepts.subList(offset, expandedConcepts.size());
    }
    Integer count = req == null ? null : req.findParameter("count").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("count").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));
    if (count != null) {
      expandedConcepts = expandedConcepts.stream().limit(count).toList();
    }

    // Decorate the windowed members with the version status / property values the FHIR expansion shape needs
    // for `inactive`/`abstract` (the SQL expand returns them bare). The exclude-inactive path already
    // decorated the full set above; here we decorate just the page, keeping the common case page-bounded.
    if (!excludeInactive) {
      decorateExpansionFlags(expandedConcepts);
    }

    // Build response ValueSet with expansion
    com.kodality.zmei.fhir.resource.terminology.ValueSet response = new com.kodality.zmei.fhir.resource.terminology.ValueSet();
    response.setUrl(inlineVs.getUrl());
    response.setVersion(inlineVs.getVersion());
    response.setName(inlineVs.getName());
    response.setTitle(inlineVs.getTitle());
    response.setStatus(inlineVs.getStatus());
    // Echo `experimental` from the source — the tx-ecosystem expects it on the $expand result (even when
    // false), and a $expand response is a rendering of the value set, so its descriptive metadata carries over.
    response.setExperimental(inlineVs.getExperimental());
    // Echo the value set's own resource `language` (when declared) onto the expansion result — the tx-ecosystem
    // expects the rendered ValueSet to carry the source VS language (distinct from the displayLanguage used to
    // pick member displays). Only the VS's declared language, not a request/extension displayLanguage.
    if (StringUtils.isNotEmpty(inlineVs.getLanguage())) {
      response.setLanguage(inlineVs.getLanguage());
    }
    // An $expand response is a rendered view, not the value-set definition — the expansion replaces the
    // compose (the tx-ecosystem marks compose optional on the expand result and the reference server omits it;
    // echoing the source compose, including its raw include version, mismatches). The stored path already
    // drops it in ValueSetFhirMapper; mirror that here.

    // Build expansion
    com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion expansion =
        new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion();
    expansion.setTimestamp(java.time.OffsetDateTime.now());
    // FHIR requires expansion.identifier (a globally-unique handle for this expansion); the tx-ecosystem
    // matches it as any uuid ($uuid$). Without it the expand tests fail "missing property identifier".
    expansion.setIdentifier("urn:uuid:" + java.util.UUID.randomUUID());
    expansion.setTotal(totalAfterFilter);
    if (offset != null) {
      expansion.setOffset(offset);
    }
    // expansion.parameter: the echoed control params (excludeNested, activeOnly, …) plus the derived
    // used-codesystem entries — same shape as the stored-snapshot path (reuses the mapper helper).
    List<com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionParameter> expansionParameters =
        ValueSetFhirMapper.expansionParameters(req, expandedConcepts);
    // used-codesystem is normally derived from the expanded members. When a filter excludes every member the
    // expansion is empty, but the code system(s) the value set drew from were still "used" — so derive
    // used-codesystem from the resolved compose includes instead (the reference engine reports it on an empty
    // search result). Only when none was derived from members, so a non-empty expansion is unaffected.
    if (expansionParameters.stream().noneMatch(pp -> "used-codesystem".equals(pp.getName()))
        && inlineVs.getCompose() != null && inlineVs.getCompose().getInclude() != null) {
      java.util.LinkedHashSet<String> includeSystems = new java.util.LinkedHashSet<>();
      for (var inc : inlineVs.getCompose().getInclude()) {
        if (inc.getSystem() != null) {
          includeSystems.add(inc.getSystem() + (inc.getVersion() != null ? "|" + inc.getVersion() : ""));
        }
      }
      includeSystems.forEach(s -> expansionParameters.add(
          new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionParameter().setName("used-codesystem").setValueUri(s)));
    }
    // The reference engine reports used-codesystem as `system|version` whenever the code system has a
    // version. The inline expand SQL does not carry the resolved CS version onto its members, and the
    // empty-expansion compose fallback above only knows the version the include pinned (often none), so
    // both can emit a bare `system`. Backfill the version from the request's tx-resource CodeSystems:
    // a versionless code system (no version on its tx-resource) stays bare, and a system that resolves
    // to more than one distinct version is left untouched (ambiguous).
    java.util.Map<String, java.util.Set<String>> txResourceVersions = new java.util.HashMap<>();
    if (req != null && req.getParameter() != null) {
      for (ParametersParameter p : req.getParameter()) {
        if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs
            && cs.getUrl() != null && StringUtils.isNotEmpty(cs.getVersion())) {
          txResourceVersions.computeIfAbsent(cs.getUrl(), k -> new java.util.HashSet<>()).add(cs.getVersion());
        }
      }
    }
    expansionParameters.stream()
        .filter(pp -> "used-codesystem".equals(pp.getName()) && pp.getValueUri() != null && !pp.getValueUri().contains("|"))
        .forEach(pp -> {
          java.util.Set<String> versions = txResourceVersions.get(pp.getValueUri());
          if (versions != null && versions.size() == 1) {
            pp.setValueUri(pp.getValueUri() + "|" + versions.iterator().next());
          }
        });
    // Derived used-supplement params (resolved url|version) for supplements applied to this inline expansion.
    usedSupplements.forEach(s -> expansionParameters.add(new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionParameter()
        .setName("used-supplement").setValueUri(s.asCanonical())));
    // Derived used-valueset params for each imported value set (compose.include.valueSet) resolved in this expansion.
    usedValueSets.stream().distinct().forEach(vsRef -> expansionParameters.add(
        new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionParameter().setName("used-valueset").setValueUri(vsRef)));
    // The display language the server resolved from the value set's own declaration (expansion-parameter
    // extension, else VS resource `language`) or — failing that — the request's Accept-Language header is echoed
    // as a displayLanguage expansion.parameter, unless the request already carried a displayLanguage param.
    String vsExpDisplayLanguage = vsDeclaredDisplayLanguage(inlineVs);
    String echoDisplayLanguage = StringUtils.isNotEmpty(vsExpDisplayLanguage) ? vsExpDisplayLanguage : acceptLanguageHeader();
    if (StringUtils.isNotEmpty(echoDisplayLanguage)
        && expansionParameters.stream().noneMatch(pp -> "displayLanguage".equals(pp.getName()))) {
      expansionParameters.add(new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionParameter()
          .setName("displayLanguage").setValueCode(echoDisplayLanguage));
    }
    // A used-codesystem must reflect the source: when the tx-resource CodeSystem declares no version, the
    // import-defaulted version (e.g. 1.0.0) must be dropped so used-codesystem is the bare system uri.
    java.util.Set<String> versionlessSystems = versionlessTxCodeSystems(req);
    if (!versionlessSystems.isEmpty()) {
      for (var pp : expansionParameters) {
        if ("used-codesystem".equals(pp.getName()) && pp.getValueUri() != null) {
          int pipe = pp.getValueUri().indexOf('|');
          if (pipe > 0 && versionlessSystems.contains(pp.getValueUri().substring(0, pipe))) {
            pp.setValueUri(pp.getValueUri().substring(0, pipe));
          }
        }
      }
    }
    // Status warnings: each used code system / the value set that is experimental, draft, deprecated or
    // withdrawn contributes a warning-<status> expansion parameter (the reference engine flags non-active
    // sources). The status comes from the tx-resource `experimental`/`status`/standards-status extension.
    for (var pp : new java.util.ArrayList<>(expansionParameters)) {
      if ("used-codesystem".equals(pp.getName()) && pp.getValueUri() != null) {
        String sys = pp.getValueUri().indexOf('|') > 0 ? pp.getValueUri().substring(0, pp.getValueUri().indexOf('|')) : pp.getValueUri();
        String w = statusWarning(txCodeSystem(req, sys));
        if (w != null) {
          expansionParameters.add(new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionParameter().setName(w).setValueUri(pp.getValueUri()));
        }
      }
    }
    String vsWarning = statusWarning(inlineVs);
    if (vsWarning != null) {
      expansionParameters.add(new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionParameter().setName(vsWarning)
          .setValueUri(inlineVs.getUrl() + (inlineVs.getVersion() != null ? "|" + inlineVs.getVersion() : "")));
    }
    expansion.setParameter(expansionParameters.isEmpty() ? null : expansionParameters);
    // Declare the requested properties so contains[].property references a declared expansion.property (valid FHIR).
    // Each declared property carries its uri — from the tx-resource CodeSystem's property definition, falling back
    // to the FHIR concept-properties uri for the built-in codes (definition/status/…).
    if (!requestedProperties.isEmpty()) {
      java.util.Map<String, String> propertyUris = propertyUris(req);
      expansion.setProperty(requestedProperties.stream()
          .map(code -> new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionProperty().setCode(code)
              .setUri(propertyUris.getOrDefault(code, "http://hl7.org/fhir/concept-properties#" + code)))
          .toList());
    }

    // Designation use codings as stated by the tx-resource CodeSystem(s), keyed by use code — so a custom
    // designation surfaces its real use system (e.g. {.../designations, olde-english}) on contains[].designation.
    java.util.Map<String, com.kodality.zmei.fhir.datatypes.Coding> designationUses = designationUses(req);
    java.util.Set<String> noLanguageDesignations = noLanguageDesignations(req);
    java.util.Set<String> noUseDesignations = noUseDesignations(req);

    // P1 display-language: pick each member's display by the effective language (requested displayLanguage →
    // VS expansion-parameter extension → the CodeSystem's resource language) and drop the chosen value from
    // the member's alternate designations, so contains[].display is the resource/requested-language one and
    // the other-language designations are kept (below).
    String vsDisplayLanguage = vsDeclaredDisplayLanguage(inlineVs);
    String acceptLanguage = acceptLanguageHeader();
    applyDisplayLanguage(expandedConcepts, displayLanguage, vsDisplayLanguage, acceptLanguage, resourceLanguages(req), primaryDisplays(req));

    // FHIR adds `version` to a contains member only when the expansion spans more than one code system
    // version (a mixed/multi-version value set), to disambiguate; a single-version expansion omits it.
    boolean emitContainsVersion = expandedConcepts.stream()
        .map(c -> c.getConcept() != null && c.getConcept().getCodeSystemVersions() != null
            ? c.getConcept().getCodeSystemVersions().stream().findFirst().orElse(null) : null)
        .filter(java.util.Objects::nonNull).distinct().count() > 1;

    // VS-scoped concept deprecation: the value set's compose can mark an enumerated concept as deprecated via a
    // valueset-deprecated / standards-status extension on the include.concept (the concept itself may be active in
    // its code system — this is a value-set-level annotation). The expand SQL drops compose-level extensions, so
    // rebuild a (system|code → extensions) map from the source compose and echo those extensions onto the matching
    // expansion.contains entry.
    java.util.Map<String, List<com.kodality.zmei.fhir.Extension>> composeConceptDeprecation = new java.util.HashMap<>();
    if (inlineVs.getCompose() != null && inlineVs.getCompose().getInclude() != null) {
      for (var inc : inlineVs.getCompose().getInclude()) {
        if (inc.getConcept() == null) {
          continue;
        }
        for (var cc : inc.getConcept()) {
          if (cc.getCode() == null || cc.getExtension() == null) {
            continue;
          }
          List<com.kodality.zmei.fhir.Extension> deps = cc.getExtension().stream()
              .filter(e -> VALUESET_DEPRECATED_URL.equals(e.getUrl()) || STANDARDS_STATUS_URL.equals(e.getUrl()))
              .toList();
          if (!deps.isEmpty()) {
            composeConceptDeprecation.put((inc.getSystem() != null ? inc.getSystem() : "") + "|" + cc.getCode(), deps);
          }
        }
      }
    }

    // Map concepts to expansion contains
    List<com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains> contains =
        expandedConcepts.stream().map(concept -> {
          com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains contain =
              new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains();
          contain.setSystem(concept.getConcept().getCodeSystemUri());
          contain.setCode(concept.getConcept().getCode());
          if (concept.getDisplay() != null) {
            contain.setDisplay(concept.getDisplay().getName());
          }
          if (emitContainsVersion && concept.getConcept().getCodeSystemVersions() != null) {
            concept.getConcept().getCodeSystemVersions().stream().findFirst().ifPresent(contain::setVersion);
          }
          // FHIR expansion.contains flags, both omitted when false: `inactive` for a retired/deprecated
          // concept, `abstract` for a not-selectable (grouper) concept. The SQL expand returns members bare,
          // so these come from decorateExpansionFlags (a bulk load of the windowed members' versions).
          if (isInactiveMember(concept)
              || (concept.getConcept() != null && txInactiveCodes.contains(concept.getConcept().getCodeSystemUri() + "|" + concept.getConcept().getCode()))) {
            contain.setInactive(true);
          }
          if (isAbstractMember(concept)
              || (concept.getConcept() != null && txAbstractCodes.contains(concept.getConcept().getCodeSystemUri() + "|" + concept.getConcept().getCode()))) {
            contain.setAbstractField(true);
          }
          // Echo any VS-scoped deprecation extensions the source compose stated for this member.
          List<com.kodality.zmei.fhir.Extension> depExts = composeConceptDeprecation.get(contain.getSystem() + "|" + contain.getCode());
          if (depExts != null) {
            depExts.forEach(contain::addExtension);
          }
          if (includeDesignations && concept.getAdditionalDesignations() != null) {
            List<com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConceptDesignation> designations =
                concept.getAdditionalDesignations().stream()
                .filter(d -> ValueSetFhirMapper.designationMatchesFilter(d, designationFilter))
                // The designation repeating the member's display is already removed (applyDisplayLanguage drops
                // it by value); the definition is surfaced as a definition property, not a designation. With no
                // explicit `designation` filter, drop only the definition — the remaining alternate-language
                // designations are kept. An explicit filter returns exactly what was asked for.
                .filter(d -> !designationFilter.isEmpty() || !"definition".equals(d.getDesignationType()))
                .map(d -> {
                  com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConceptDesignation designation =
                      new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConceptDesignation();
                  designation.setValue(d.getName());
                  // Emit the designation's `language`, EXCEPT where the tx-resource CodeSystem shows this
                  // designation has no stated language (import defaults a missing language to the resource
                  // language, but the tx-ecosystem omits it). For a stored code system the set is empty, so the
                  // stored language is preserved.
                  if (!noLanguageDesignations.contains(d.getName())) {
                    designation.setLanguage(d.getLanguage());
                  }
                  // A designation the source stated WITHOUT a use is echoed without one (the import defaults a
                  // missing use to type "display", which must not resurface as a use coding). Otherwise prefer the
                  // designation's own use coding from the tx-resource CodeSystem (it carries the use system for
                  // custom designations, e.g. {.../designations, olde-english}); fall back to the type→use mapping.
                  if (!noUseDesignations.contains(d.getName())) {
                    com.kodality.zmei.fhir.datatypes.Coding use = designationUses.get(d.getDesignationType());
                    designation.setUse(use != null ? use
                        : org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper.designationUseCoding(
                            d.getDesignationType() == null ? "display" : d.getDesignationType()));
                  }
                  return designation;
                }).toList();
            if (!designations.isEmpty()) {
              contain.setDesignation(designations);
            }
          }
          if (!requestedProperties.isEmpty()) {
            List<com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContainsProperty> props =
                ValueSetFhirMapper.toFhirContainsProperties(concept.getPropertyValues(), requestedProperties::contains, displayLanguage);
            if (!props.isEmpty()) {
              contain.setProperty(props);
            }
          }
          return contain;
        }).toList();

    // FHIR hierarchical expansion: when excludeNested is explicitly false, nest each member under its parent
    // (ValueSet.expansion.contains.contains) instead of returning a flat list. Membership/total stay flat; this
    // only reshapes the presentation. The parent of each code comes from the tx-resource CodeSystem's nested
    // concept tree. When excludeNested is absent or true, the flat list is kept (the default and prior behaviour).
    // An enumerated value set (explicit compose.include.concept list) is a flat selection, not a view of the
    // code system hierarchy — it is never nested, even with excludeNested=false.
    boolean enumerated = !expandedConcepts.isEmpty() && expandedConcepts.stream().allMatch(ValueSetVersionConcept::isEnumerated);
    // Multi-version "overload": when a code system is included at more than one version, the reference engine
    // orders the expansion by code ascending then version descending (code1@2.0.0, code1@1.0.0, code2@2.0.0, …),
    // not by include order. Scope this narrowly — only a NON-enumerated expansion that actually spans more than
    // one version — so an enumerated value set keeps its definition order and a single-version expansion (the
    // overwhelming majority) is untouched.
    long distinctVersions = contains.stream()
        .map(com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains::getVersion)
        .filter(java.util.Objects::nonNull).distinct().count();
    if (!enumerated && distinctVersions > 1) {
      List<com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains> ordered = new java.util.ArrayList<>(contains);
      ordered.sort((a, b) -> {
        int byCode = java.util.Comparator.nullsLast(String::compareTo).compare(a.getCode(), b.getCode());
        if (byCode != 0) {
          return byCode;
        }
        if (a.getVersion() == null || b.getVersion() == null) {
          return java.util.Comparator.nullsLast(String::compareTo).compare(b.getVersion(), a.getVersion());
        }
        return compareVersions(b.getVersion(), a.getVersion()); // version descending within a code
      });
      contains = ordered;
    }
    boolean nestHierarchy = !enumerated && req != null && req.findParameter("excludeNested").isPresent()
        && req.findParameter("excludeNested")
            .map(p -> !(Boolean.TRUE.equals(p.getValueBoolean()) || "true".equals(p.getValueString()))).orElse(false);
    if (nestHierarchy) {
      contains = nestContains(contains, hierarchyParents(req));
    }
    expansion.setContains(contains);

    response.setExpansion(expansion);
    return response;
  }

  /** CodeSystem property code→uri from the tx-resource CodeSystems' property definitions. */
  private static java.util.Map<String, String> propertyUris(Parameters req) {
    java.util.Map<String, String> uris = new java.util.HashMap<>();
    if (req == null || req.getParameter() == null) {
      return uris;
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs
          && cs.getProperty() != null) {
        for (var prop : cs.getProperty()) {
          if (prop.getCode() != null && prop.getUri() != null) {
            uris.putIfAbsent(prop.getCode(), prop.getUri());
          }
        }
      }
    }
    return uris;
  }

  /** Designation use codings stated by the tx-resource CodeSystems, keyed by the use code (so custom designations keep their use system). */
  private static java.util.Map<String, com.kodality.zmei.fhir.datatypes.Coding> designationUses(Parameters req) {
    java.util.Map<String, com.kodality.zmei.fhir.datatypes.Coding> uses = new java.util.HashMap<>();
    if (req == null || req.getParameter() == null) {
      return uses;
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs) {
        collectDesignationUses(cs.getConcept(), uses);
      }
    }
    return uses;
  }

  private static void collectDesignationUses(List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept> concepts,
                                             java.util.Map<String, com.kodality.zmei.fhir.datatypes.Coding> uses) {
    if (concepts == null) {
      return;
    }
    for (var c : concepts) {
      if (c.getDesignation() != null) {
        for (var d : c.getDesignation()) {
          if (d.getUse() != null && d.getUse().getCode() != null) {
            uses.putIfAbsent(d.getUse().getCode(), d.getUse());
          }
        }
      }
      collectDesignationUses(c.getConcept(), uses);
    }
  }

  /** Designation values that the tx-resource CodeSystems state WITHOUT a language (so the import-defaulted language is dropped on echo). */
  private static java.util.Set<String> noLanguageDesignations(Parameters req) {
    java.util.Set<String> values = new java.util.HashSet<>();
    if (req == null || req.getParameter() == null) {
      return values;
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs) {
        collectNoLanguageDesignations(cs.getConcept(), values);
      }
    }
    return values;
  }

  private static void collectNoLanguageDesignations(List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept> concepts,
                                                    java.util.Set<String> values) {
    if (concepts == null) {
      return;
    }
    for (var c : concepts) {
      if (c.getDesignation() != null) {
        for (var d : c.getDesignation()) {
          if (d.getValue() != null && d.getLanguage() == null) {
            values.add(d.getValue());
          }
        }
      }
      collectNoLanguageDesignations(c.getConcept(), values);
    }
  }

  /** {@code CodeSystem.url → resource language} from the tx-resource CodeSystems — used as the default
   *  displayLanguage when the request states none (a member's display is then its resource-language one). */
  private static java.util.Map<String, String> resourceLanguages(Parameters req) {
    java.util.Map<String, String> langs = new java.util.HashMap<>();
    if (req == null || req.getParameter() == null) {
      return langs;
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs
          && StringUtils.isNotEmpty(cs.getUrl()) && StringUtils.isNotEmpty(cs.getLanguage())) {
        langs.putIfAbsent(cs.getUrl(), cs.getLanguage());
      }
    }
    return langs;
  }

  /** {@code system|code → CodeSystem.concept.display} (the PRIMARY display) from the tx-resource CodeSystems —
   *  so the display re-pick prefers the concept's own primary display over an alternate same-language designation. */
  private static java.util.Map<String, String> primaryDisplays(Parameters req) {
    java.util.Map<String, String> displays = new java.util.HashMap<>();
    if (req == null || req.getParameter() == null) {
      return displays;
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs
          && StringUtils.isNotEmpty(cs.getUrl())) {
        collectPrimaryDisplays(cs.getUrl(), cs.getConcept(), displays);
      }
    }
    return displays;
  }

  private static void collectPrimaryDisplays(String system, List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept> concepts,
                                             java.util.Map<String, String> displays) {
    if (concepts == null) {
      return;
    }
    for (var c : concepts) {
      if (c.getCode() != null && StringUtils.isNotEmpty(c.getDisplay())) {
        displays.putIfAbsent(system + "|" + c.getCode(), c.getDisplay());
      }
      collectPrimaryDisplays(system, c.getConcept(), displays);
    }
  }

  /** The primary language tag of the request's {@code Accept-Language} header (e.g. {@code en} from
   *  {@code en-US,en;q=0.9}), or null. The LOWEST-priority display-language source — below an explicit
   *  {@code displayLanguage} param and the value set's own declared language. */
  private static String acceptLanguageHeader() {
    return io.micronaut.http.context.ServerRequestContext.currentRequest()
        .map(r -> r.getHeaders().get("Accept-Language"))
        .filter(StringUtils::isNotEmpty)
        .map(h -> h.split(",")[0].split(";")[0].trim())
        .filter(StringUtils::isNotEmpty)
        .orElse(null);
  }

  /** The display language a value set itself declares — its {@code compose.extension[valueset-expansion-parameter]}
   *  {@code displayLanguage}, else the VS resource {@code language}. Applied AND echoed as an expansion.parameter.
   *  (Ranks below an explicit request {@code displayLanguage}; an Accept-Language header would rank below this.) */
  private static String vsDeclaredDisplayLanguage(com.kodality.zmei.fhir.resource.terminology.ValueSet vs) {
    String ext = vsExpansionDisplayLanguage(vs);
    return StringUtils.isNotEmpty(ext) ? ext : (vs != null ? vs.getLanguage() : null);
  }

  /** The {@code displayLanguage} declared by a value set's {@code compose.extension[valueset-expansion-parameter]}
   *  (a VS-embedded expansion control) — applied AND echoed as an {@code expansion.parameter}. */
  private static String vsExpansionDisplayLanguage(com.kodality.zmei.fhir.resource.terminology.ValueSet vs) {
    if (vs == null || vs.getCompose() == null || vs.getCompose().getExtension() == null) {
      return null;
    }
    for (com.kodality.zmei.fhir.Extension ext : vs.getCompose().getExtension()) {
      if (!"http://hl7.org/fhir/StructureDefinition/valueset-expansion-parameter".equals(ext.getUrl()) || ext.getExtension() == null) {
        continue;
      }
      String name = ext.getExtension().stream().filter(e -> "name".equals(e.getUrl()))
          .map(e -> e.getValueString() != null ? e.getValueString() : e.getValueCode()).filter(java.util.Objects::nonNull).findFirst().orElse(null);
      if ("displayLanguage".equals(name)) {
        return ext.getExtension().stream().filter(e -> "value".equals(e.getUrl()))
            .map(e -> e.getValueCode() != null ? e.getValueCode() : e.getValueString()).filter(java.util.Objects::nonNull).findFirst().orElse(null);
      }
    }
    return null;
  }

  /** Designation VALUES the tx-resource CodeSystem stated with NO {@code use} — kept on contains[].designation
   *  without a `use` coding (the import defaults a missing use to type "display", which must not resurface). */
  private static java.util.Set<String> noUseDesignations(Parameters req) {
    java.util.Set<String> values = new java.util.HashSet<>();
    if (req == null || req.getParameter() == null) {
      return values;
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs) {
        collectNoUseDesignations(cs.getConcept(), values);
      }
    }
    return values;
  }

  private static void collectNoUseDesignations(List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept> concepts,
                                               java.util.Set<String> values) {
    if (concepts == null) {
      return;
    }
    for (var c : concepts) {
      if (c.getDesignation() != null) {
        for (var d : c.getDesignation()) {
          if (d.getValue() != null && (d.getUse() == null || d.getUse().getCode() == null)) {
            values.add(d.getValue());
          }
        }
      }
      collectNoUseDesignations(c.getConcept(), values);
    }
  }

  /**
   * P1 display-language: choose each member's display by the effective language — the requested
   * {@code displayLanguage}, else the VS expansion-parameter extension, else the member's CodeSystem resource
   * language — re-picking from the member's own designations (display + additional). When the effective
   * language is unknown (no language in play and no tx-resource resource language), the display is left as
   * the SQL/provider chose it. The chosen display is then dropped from the member's additional designations
   * (by value) so it is not also echoed as a designation.
   */
  private static void applyDisplayLanguage(List<ValueSetVersionConcept> concepts, String requestedLanguage,
                                           String vsDisplayLanguage, String acceptLanguage, java.util.Map<String, String> resourceLanguages,
                                           java.util.Map<String, String> primaryDisplays) {
    for (ValueSetVersionConcept c : concepts) {
      // Only re-pick WHICH designation is the display; never invent a display for a member that has none
      // (that would surface an "unexpected" display where the reference omits it).
      if (c.getDisplay() == null) {
        continue;
      }
      String system = c.getConcept() != null ? c.getConcept().getCodeSystemUri() : null;
      String code = c.getConcept() != null ? c.getConcept().getCode() : null;
      String resourceLanguage = system != null ? resourceLanguages.get(system) : null;
      // Precedence: explicit displayLanguage param > VS-declared language > CodeSystem resource language >
      // Accept-Language header (the header ranks BELOW the resource/VS language, per decision 2026-06-20).
      String effective = StringUtils.isNotEmpty(requestedLanguage) ? requestedLanguage
          : StringUtils.isNotEmpty(vsDisplayLanguage) ? vsDisplayLanguage
          : StringUtils.isNotEmpty(resourceLanguage) ? resourceLanguage
          : acceptLanguage;
      // De-dupe display + additional designations by (language, value) — the SQL expand can carry the display
      // value as an additional designation too, which would otherwise resurface as a duplicate designation.
      java.util.LinkedHashMap<String, Designation> all = new java.util.LinkedHashMap<>();
      all.putIfAbsent(c.getDisplay().getLanguage() + "|" + c.getDisplay().getName(), c.getDisplay());
      if (c.getAdditionalDesignations() != null) {
        c.getAdditionalDesignations().forEach(d -> all.putIfAbsent(d.getLanguage() + "|" + d.getName(), d));
      }
      String primaryDisplay = system != null && code != null ? primaryDisplays.get(system + "|" + code) : null;
      final String eff = effective;
      Designation chosen = c.getDisplay();
      if (StringUtils.isNotEmpty(eff)) {
        // Among language-matching candidates, prefer (in order) an EXACT language match over a region-subtag
        // match (`de` over `de-CH`), and the concept's PRIMARY display value over an alternate same-language
        // designation. Scored: exact-language = 2, primary-display value = 1.
        // A `definition`-use designation is NOT a display name (FHIR), so it must never be chosen as the
        // display — e.g. a `de` definition must not surface as the `de` display, which would otherwise leave a
        // requested-language definition masquerading as the display (the reference keeps the default display).
        chosen = all.values().stream()
            .filter(d -> !"definition".equals(d.getDesignationType()))
            .filter(d -> languageMatches(d.getLanguage(), eff))
            .max(java.util.Comparator.comparingInt(d ->
                (eff.equalsIgnoreCase(d.getLanguage()) ? 2 : 0)
                    + (d.getName() != null && d.getName().equals(primaryDisplay) ? 1 : 0)))
            .orElse(c.getDisplay());
      }
      final Designation display = chosen;
      c.setDisplay(display);
      c.setAdditionalDesignations(all.values().stream().filter(d -> d != display).toList());
    }
  }

  /** BCP-47-ish language match: exact or a region refinement (`de` matches `de-DE`). */
  private static boolean languageMatches(String language, String target) {
    return language != null && target != null
        && (language.equals(target) || language.startsWith(target + "-") || target.startsWith(language + "-"));
  }

  /** code-system child→parent map ({@code system|code} → parent code) from the tx-resource CodeSystems' nested concept trees. */
  private static java.util.Map<String, String> hierarchyParents(Parameters req) {
    java.util.Map<String, String> parents = new java.util.HashMap<>();
    if (req == null || req.getParameter() == null) {
      return parents;
    }
    for (ParametersParameter p : req.getParameter()) {
      if ("tx-resource".equals(p.getName()) && p.getResource() instanceof com.kodality.zmei.fhir.resource.terminology.CodeSystem cs) {
        collectParents(cs.getConcept(), cs.getUrl(), null, parents);
      }
    }
    return parents;
  }

  private static void collectParents(List<com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept> concepts,
                                     String system, String parentCode, java.util.Map<String, String> parents) {
    if (concepts == null) {
      return;
    }
    for (var c : concepts) {
      if (parentCode != null) {
        parents.put(system + "|" + c.getCode(), parentCode);
      }
      collectParents(c.getConcept(), system, c.getCode(), parents);
    }
  }

  /** Reshapes a flat (ordered) contains list into a tree: each member whose parent is also present nests under it; the rest are roots. */
  private static List<com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains> nestContains(
      List<com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains> flat, java.util.Map<String, String> parents) {
    java.util.Map<String, com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains> byCode = new java.util.LinkedHashMap<>();
    for (var c : flat) {
      byCode.put(c.getSystem() + "|" + c.getCode(), c);
    }
    List<com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains> roots = new java.util.ArrayList<>();
    for (var c : flat) {
      String parentCode = parents.get(c.getSystem() + "|" + c.getCode());
      var parent = parentCode == null ? null : byCode.get(c.getSystem() + "|" + parentCode);
      if (parent != null) {
        if (parent.getContains() == null) {
          parent.setContains(new java.util.ArrayList<>());
        }
        parent.getContains().add(c);
      } else {
        roots.add(c);
      }
    }
    return roots;
  }

  /**
   * Decorates the (already paged) inline-expansion members with the version {@code status} and property
   * values the FHIR {@code inactive}/{@code abstract} flags need, in a single bulk query keyed by the
   * member's code system entity version id (which the SQL expand DOES populate). The inline SQL path returns
   * members without these, so without this they'd all look active and selectable.
   */
  /** The values of a repeated request parameter (e.g. {@code property}, {@code designation}), code or string. */
  private static List<String> designationOrPropertyRequested(Parameters req, String name) {
    return req == null || req.getParameter() == null ? List.of() :
        req.getParameter().stream().filter(p -> name.equals(p.getName()))
            .map(p -> p.getValueCode() != null ? p.getValueCode() : p.getValueString())
            .filter(StringUtils::isNotEmpty).toList();
  }

  /** Loads concept property values onto the (already expanded) members, by code system entity version id — the stored snapshot omits them. */
  private void decoratePropertyValues(List<ValueSetVersionConcept> concepts) {
    String ids = concepts.stream()
        .map(c -> c.getConcept() != null ? c.getConcept().getConceptVersionId() : null)
        .filter(java.util.Objects::nonNull).distinct().map(String::valueOf)
        .collect(java.util.stream.Collectors.joining(","));
    if (StringUtils.isEmpty(ids)) {
      return;
    }
    org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams params = new org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams();
    params.setIds(ids);
    params.setLimit(-1);
    java.util.Map<Long, CodeSystemEntityVersion> byId = codeSystemEntityVersionService.query(params).getData().stream()
        .collect(java.util.stream.Collectors.toMap(CodeSystemEntityVersion::getId, v -> v, (a, b) -> a));
    concepts.forEach(c -> {
      Long vid = c.getConcept() != null ? c.getConcept().getConceptVersionId() : null;
      CodeSystemEntityVersion v = vid != null ? byId.get(vid) : null;
      if (v != null && (c.getPropertyValues() == null || c.getPropertyValues().isEmpty())) {
        c.setPropertyValues(v.getPropertyValues());
      }
    });
  }

  private void decorateExpansionFlags(List<ValueSetVersionConcept> concepts) {
    String ids = concepts.stream()
        .map(c -> c.getConcept() != null ? c.getConcept().getConceptVersionId() : null)
        .filter(java.util.Objects::nonNull).distinct().map(String::valueOf)
        .collect(java.util.stream.Collectors.joining(","));
    if (StringUtils.isEmpty(ids)) {
      return;
    }
    org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams params = new org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams();
    params.setIds(ids);
    params.setLimit(-1);
    java.util.Map<Long, CodeSystemEntityVersion> byId = codeSystemEntityVersionService.query(params).getData().stream()
        .collect(java.util.stream.Collectors.toMap(CodeSystemEntityVersion::getId, v -> v, (a, b) -> a));
    concepts.forEach(c -> {
      Long vid = c.getConcept() != null ? c.getConcept().getConceptVersionId() : null;
      CodeSystemEntityVersion v = vid != null ? byId.get(vid) : null;
      if (v != null) {
        c.setStatus(v.getStatus());
        c.setPropertyValues(v.getPropertyValues());
        // The SQL expand carries the code system version *id* but not its number; the loaded entity version
        // knows the version(s) it belongs to, so surface them — `used-codesystem` reports `system|version`.
        List<String> existing = c.getConcept() != null ? c.getConcept().getCodeSystemVersions() : List.of();
        if (existing == null || existing.isEmpty()) {
          List<String> versions = java.util.Optional.ofNullable(v.getVersions()).orElse(List.of()).stream()
              .map(org.termx.ts.codesystem.CodeSystemVersionReference::getVersion)
              .filter(java.util.Objects::nonNull).distinct().toList();
          if (!versions.isEmpty()) {
            c.getConcept().setCodeSystemVersions(versions);
          }
        }
      }
    });
  }

  /**
   * A member is inactive when its concept version status is retired/deprecated, OR it carries the FHIR
   * concept-property {@code inactive=true}, OR a {@code status} property of retired/deprecated/inactive — the
   * tx-ecosystem marks such members {@code inactive} in the expansion and excludes them under {@code activeOnly}.
   */
  private static boolean isInactiveMember(ValueSetVersionConcept concept) {
    if (List.of("retired", "deprecated", "inactive").contains(String.valueOf(concept.getStatus()))) {
      return true;
    }
    return java.util.Optional.ofNullable(concept.getPropertyValues()).orElse(List.of()).stream().anyMatch(pv ->
        ("inactive".equals(pv.getEntityProperty()) && (Boolean.TRUE.equals(pv.getValue()) || "true".equalsIgnoreCase(String.valueOf(pv.getValue()))))
            || ("status".equals(pv.getEntityProperty()) && List.of("retired", "deprecated", "inactive").contains(String.valueOf(pv.getValue()))));
  }

  /** A member is abstract (a grouper, not for direct use) when its concept carries {@code notSelectable=true}. */
  private static boolean isAbstractMember(ValueSetVersionConcept concept) {
    return java.util.Optional.ofNullable(concept.getPropertyValues()).orElse(List.of()).stream()
        .filter(pv -> "notSelectable".equals(pv.getEntityProperty()))
        .anyMatch(pv -> Boolean.TRUE.equals(pv.getValue()) || "true".equalsIgnoreCase(String.valueOf(pv.getValue())));
  }


  // ===========================================================================
  // SNOMED CT implicit-ValueSet URLs (`?fhir_vs[=...]`)
  // ===========================================================================

  /**
   * If {@code url} is a SNOMED CT canonical with a {@code ?fhir_vs} query, delegate
   * expansion to the SNOMED {@link ValueSetExternalExpandProvider} (which in turn
   * calls Snowstorm via SnomedService with an ECL expression) and return the
   * built response. Returns {@code null} when the URL is not a SNOMED implicit
   * canonical so the caller falls through to the stored-VS lookup.
   *
   * <p>Supported patterns per the HL7 UTG SNOMED CT page:
   * <ul>
   *   <li>{@code ?fhir_vs}                 → ECL {@code *}        (all concepts)</li>
   *   <li>{@code ?fhir_vs=isa/<sctid>}     → ECL {@code <<<sctid>} (subsumed-by-or-self)</li>
   *   <li>{@code ?fhir_vs=refset}          → ECL {@code <<900000000000455006}</li>
   *   <li>{@code ?fhir_vs=refset/<sctid>}  → ECL {@code ^<sctid>}  (refset members)</li>
   *   <li>{@code ?fhir_vs=ecl/<expr>}      → ECL {@code <expr>}    (URL-decoded)</li>
   * </ul>
   */
  private com.kodality.zmei.fhir.resource.terminology.ValueSet tryExpandSnomedImplicit(String url, Parameters req) {
    if (url == null || !url.startsWith(SNOMED_BASE_URL)) {
      return null;
    }
    int qIdx = url.indexOf('?');
    if (qIdx < 0) {
      return null;
    }
    String baseAndVersion = url.substring(0, qIdx);
    String query = url.substring(qIdx + 1);
    if (!query.equals("fhir_vs") && !query.startsWith("fhir_vs=")) {
      return null;
    }
    String fhirVsParam = query.equals("fhir_vs") ? "" : query.substring("fhir_vs=".length());

    ValueSetRuleFilter filter = new ValueSetRuleFilter();
    if (fhirVsParam.isEmpty()) {
      // All concepts of the edition/version — ECL `*` matches anything.
      filter.setOperator("ecl");
      filter.setValue("*");
    } else if (fhirVsParam.startsWith("isa/")) {
      filter.setOperator("is-a");
      filter.setValue(fhirVsParam.substring("isa/".length()));
    } else if (fhirVsParam.equals("refset")) {
      // All reference sets — descendants-or-self of the Reference set foundation.
      filter.setOperator("is-a");
      filter.setValue(SNOMED_REFSET_FOUNDATION_SCTID);
    } else if (fhirVsParam.startsWith("refset/")) {
      filter.setOperator("in");
      filter.setValue(fhirVsParam.substring("refset/".length()));
    } else if (fhirVsParam.startsWith("ecl/")) {
      filter.setOperator("ecl");
      filter.setValue(URLDecoder.decode(fhirVsParam.substring("ecl/".length()), StandardCharsets.UTF_8));
    } else {
      throw new FhirException(400, IssueType.INVALID, "unrecognised SNOMED ?fhir_vs pattern: " + fhirVsParam);
    }

    ValueSetVersionRule rule = new ValueSetVersionRule();
    rule.setCodeSystem(SNOMED_CS_ID);
    rule.setType("include");
    rule.setFilters(List.of(filter));
    if (baseAndVersion.length() > SNOMED_BASE_URL.length()) {
      // Versioned canonical `http://snomed.info/sct/<edition>/version/<YYYYMMDD>`.
      // The provider's getBranch(...) helper parses the URI and resolves to a Snowstorm branch.
      CodeSystemVersionReference vsr = new CodeSystemVersionReference();
      vsr.setUri(baseAndVersion);
      rule.setCodeSystemVersion(vsr);
    }

    ValueSetExternalExpandProvider provider = findSnomedProvider();
    if (provider == null) {
      throw new FhirException(501, IssueType.NOTSUPPORTED,
          "SNOMED expansion provider is not configured: " + url);
    }

    String displayLanguage = req == null ? null : req.findParameter("displayLanguage").map(ParametersParameter::getValueCode)
        .orElse(req.findParameter("displayLanguage").map(ParametersParameter::getValueString).orElse(null));

    // Stub version — SnomedValueSetExpandProvider only reads supportedLanguages
    // and preferredLanguage from it, both nullable.
    ValueSetVersion stubVersion = new ValueSetVersion();

    String textFilter = req == null ? null : req.findParameter("filter")
        .map(pp -> pp.getValueString() != null ? pp.getValueString()
            : pp.getValueCode() != null ? pp.getValueCode() : pp.getValueUri()).orElse(null);
    Integer offset = req == null ? null : req.findParameter("offset").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("offset").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));
    Integer count = req == null ? null : req.findParameter("count").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("count").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));

    // Push paging + free-text filter down to Snowstorm. The previous implementation
    // called provider.ruleExpand (which uses setAll(true) → loops Snowstorm at
    // limit=9999) and sliced in memory — for `?fhir_vs` (ECL=*) against a
    // SNOMED edition that materialised the entire concept set into heap before
    // discarding all but `count` items. Real-world OOM, fixed by paging at the
    // source.
    QueryResult<ValueSetVersionConcept> page = provider.ruleExpandPaged(rule, stubVersion, displayLanguage, textFilter, offset, count);
    List<ValueSetVersionConcept> concepts = page.getData() != null ? page.getData() : Collections.emptyList();
    Integer totalBeforePaging = page.getMeta() != null && page.getMeta().getTotal() != null
        ? page.getMeta().getTotal()
        : concepts.size();

    com.kodality.zmei.fhir.resource.terminology.ValueSet response = new com.kodality.zmei.fhir.resource.terminology.ValueSet();
    response.setUrl(url);
    response.setStatus("active");

    com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion expansion =
        new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion();
    expansion.setTimestamp(java.time.OffsetDateTime.now());
    expansion.setTotal(totalBeforePaging);
    if (offset != null) {
      expansion.setOffset(offset);
    }
    expansion.setContains(concepts.stream().map(c -> {
      com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains contain =
          new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains();
      contain.setSystem(c.getConcept() != null ? c.getConcept().getCodeSystemUri() : SNOMED_BASE_URL);
      contain.setCode(c.getConcept() != null ? c.getConcept().getCode() : null);
      if (c.getDisplay() != null) {
        contain.setDisplay(c.getDisplay().getName());
      }
      return contain;
    }).toList());
    response.setExpansion(expansion);
    return response;
  }

  private ValueSetExternalExpandProvider findSnomedProvider() {
    if (externalExpandProviders == null) {
      return null;
    }
    return externalExpandProviders.stream()
        .filter(p -> SNOMED_CS_ID.equals(p.getCodeSystemId()))
        .findFirst()
        .orElse(null);
  }


  // ===========================================================================
  // Implicit ValueSet over a stored CodeSystem
  // ===========================================================================

  /**
   * If {@code url} matches a stored CodeSystem URI, delegate expansion to the
   * internal {@link ConceptService#query} API. This bypasses the SQL function
   * `value_set_expand_jsonb`, which is built for arbitrary compose rules and
   * doesn't fetch designations for system-only includes; the concept-query API
   * does both display resolution and DB-level pagination correctly. Returns
   * {@code null} when no matching CodeSystem exists so the caller can 404.
   *
   * <p>Tolerated URL forms:
   * <ul>
   *   <li>{@code <canonical>}                       — bare canonical</li>
   *   <li>{@code <canonical>?fhir_vs}               — bare fhir_vs suffix is stripped (SNOMED-only when valued)</li>
   *   <li>{@code <canonical>|<version>}             — pipe-version syntax</li>
   *   <li>{@code <canonical>|<version>?fhir_vs}     — combination of the above</li>
   * </ul>
   * The {@code valueSetVersion} parameter wins when both it and the pipe form
   * carry a version.
   */
  private com.kodality.zmei.fhir.resource.terminology.ValueSet tryExpandCodeSystemImplicit(String url, String versionNr, Parameters req) {
    if (url == null) {
      return null;
    }
    String stripped = url;
    // Strip bare ?fhir_vs suffix. Non-SNOMED ?fhir_vs=<pattern> URLs aren't
    // interpreted — they fall through and 404.
    int qIdx = stripped.indexOf('?');
    if (qIdx >= 0) {
      String query = stripped.substring(qIdx + 1);
      if (!"fhir_vs".equals(query) && !query.isEmpty()) {
        return null;
      }
      stripped = stripped.substring(0, qIdx);
    }
    // Parse `|<version>` canonical-with-version syntax.
    String pipeVersion = null;
    int pIdx = stripped.indexOf('|');
    if (pIdx >= 0) {
      pipeVersion = stripped.substring(pIdx + 1);
      stripped = stripped.substring(0, pIdx);
    }
    if (stripped.isEmpty()) {
      return null;
    }

    // Resolve the CodeSystem by URI. Empty result → fall through to 404.
    CodeSystemQueryParams csParams = new CodeSystemQueryParams();
    csParams.setUri(stripped);
    csParams.setLimit(1);
    CodeSystem cs = codeSystemService.query(csParams).findFirst().orElse(null);
    if (cs == null) {
      return null;
    }

    String resolvedVersion = versionNr != null ? versionNr : pipeVersion;

    // Extract paging + filter + display-language parameters
    String displayLanguage = req == null ? null : req.findParameter("displayLanguage").map(ParametersParameter::getValueCode)
        .orElse(req.findParameter("displayLanguage").map(ParametersParameter::getValueString)
        .orElse(req.findParameter("defaultLanguage").map(ParametersParameter::getValueCode)
        .orElse(req.findParameter("defaultLanguage").map(ParametersParameter::getValueString).orElse(null))));
    String textFilter = req == null ? null : req.findParameter("filter")
        .map(pp -> pp.getValueString() != null ? pp.getValueString()
            : pp.getValueCode() != null ? pp.getValueCode() : pp.getValueUri()).orElse(null);
    Integer offset = req == null ? null : req.findParameter("offset").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("offset").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));
    Integer count = req == null ? null : req.findParameter("count").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("count").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));

    // Delegate to the concept-query API — same path the UI's concept list uses.
    // The API matches `textContains` against both code and display, returns
    // designations for each concept, supports DB-level paging, and reports the
    // pre-paging total in QueryResult.meta.
    ConceptQueryParams cqParams = new ConceptQueryParams();
    cqParams.setCodeSystem(cs.getId());
    if (resolvedVersion != null) {
      cqParams.setCodeSystemVersion(resolvedVersion);
    }
    if (textFilter != null && !textFilter.isBlank()) {
      cqParams.setTextContains(textFilter);
    }
    if (displayLanguage != null) {
      cqParams.setDisplayLanguage(displayLanguage);
    }
    if (count != null) {
      cqParams.setLimit(count);
    }
    if (offset != null) {
      cqParams.setOffset(offset);
    }

    QueryResult<Concept> result = conceptService.query(cqParams);
    Integer total = result.getMeta() != null ? result.getMeta().getTotal() : null;
    List<Concept> concepts = result.getData() != null ? result.getData() : Collections.emptyList();

    com.kodality.zmei.fhir.resource.terminology.ValueSet response = new com.kodality.zmei.fhir.resource.terminology.ValueSet();
    response.setUrl(stripped);
    response.setStatus("active");

    com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion expansion =
        new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion();
    expansion.setTimestamp(java.time.OffsetDateTime.now());
    expansion.setTotal(total != null ? total : concepts.size());
    if (offset != null) {
      expansion.setOffset(offset);
    }

    final String systemUri = stripped;
    final String displayLang = displayLanguage;
    expansion.setContains(concepts.stream().map(c -> {
      com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains contain =
          new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains();
      contain.setSystem(systemUri);
      contain.setCode(c.getCode());
      // Pick the right display using the same helper CodeSystemLookupOperation uses.
      List<Designation> designations = c.getVersions() != null && !c.getVersions().isEmpty() && c.getVersions().get(0).getDesignations() != null
          ? c.getVersions().get(0).getDesignations()
          : Collections.emptyList();
      Designation display = ConceptUtil.getDisplay(designations, displayLang, List.of());
      if (display != null) {
        contain.setDisplay(display.getName());
      }
      return contain;
    }).toList());
    response.setExpansion(expansion);
    return response;
  }
}
