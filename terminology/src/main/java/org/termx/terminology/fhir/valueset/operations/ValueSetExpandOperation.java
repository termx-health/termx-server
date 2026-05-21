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
import org.termx.terminology.terminology.valueset.ValueSetService;
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService;
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptRepository;
import org.termx.ts.codesystem.CodeSystemVersionReference;
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

    // 2. Fall back to url-based lookup (existing logic)
    String url = req.findParameter("url").map(pp -> pp.getValueUrl() != null ? pp.getValueUrl() : pp.getValueString())
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "parameter 'url' or 'valueSet' required"));
    String versionNr = req.findParameter("valueSetVersion").map(ParametersParameter::getValueString).orElse(null);

    // 3. SNOMED CT implicit-ValueSet URLs (`http://snomed.info/sct[/<edition>/version/<date>]?fhir_vs[=...]`)
    //    are recognised before the stored-VS lookup and delegated to the
    //    SnomedValueSetExpandProvider, which already calls Snowstorm with an
    //    ECL expression.
    com.kodality.zmei.fhir.resource.terminology.ValueSet snomedResp = tryExpandSnomedImplicit(url, req);
    if (snomedResp != null) {
      return snomedResp;
    }

    ValueSetQueryParams vsParams = new ValueSetQueryParams();
    vsParams.setUri(url);
    vsParams.setLimit(1);
    vsParams.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.VS_READ));
    ValueSet valueSet = valueSetService.query(vsParams).findFirst()
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "value set not found: " + url));

    return expand(valueSet, versionNr, req);
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

    String displayLanguage = req == null ? null : req.findParameter("displayLanguage").map(ParametersParameter::getValueCode)
        .orElse(req.findParameter("displayLanguage").map(ParametersParameter::getValueString)
        .orElse(req.findParameter("defaultLanguage").map(ParametersParameter::getValueCode)
        .orElse(req.findParameter("defaultLanguage").map(ParametersParameter::getValueString).orElse(null))));
    boolean includeDesignations = req != null && req.findParameter("includeDesignations")
        .map(pr -> pr.getValueBoolean() != null && pr.getValueBoolean() || "true".equals(pr.getValueString()))
        .orElse(false);

    ValueSetSnapshot snapshot = valueSetVersionConceptService.expand(vs.getId(), version.getVersion(), displayLanguage, includeDesignations);
    List<ValueSetVersionConcept> expandedConcepts = snapshot.getExpansion();
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
    // while preserving the original conceptsTotal for FHIR-correct expansion.total.
    if (expandedConcepts != snapshot.getExpansion()) {
      snapshot = new ValueSetSnapshot()
          .setId(snapshot.getId())
          .setValueSet(snapshot.getValueSet())
          .setValueSetVersion(snapshot.getValueSetVersion())
          .setConceptsTotal(snapshot.getConceptsTotal())
          .setExpansion(expandedConcepts)
          .setDependencies(snapshot.getDependencies())
          .setCreatedAt(snapshot.getCreatedAt())
          .setCreatedBy(snapshot.getCreatedBy());
    }

    return mapper.toFhir(vs, version, provenances, snapshot, req);
  }

  private com.kodality.zmei.fhir.resource.terminology.ValueSet expandInline(
      com.kodality.zmei.fhir.resource.terminology.ValueSet inlineVs, Parameters req) {
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

    // Apply paging
    Integer offset = req == null ? null : req.findParameter("offset").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("offset").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));
    if (offset != null) {
      expandedConcepts = offset > expandedConcepts.size() ? List.of() : expandedConcepts.subList(offset, expandedConcepts.size());
    }
    Integer count = req == null ? null : req.findParameter("count").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("count").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));
    if (count != null) {
      expandedConcepts = expandedConcepts.stream().limit(count).toList();
    }

    // Build response ValueSet with expansion
    com.kodality.zmei.fhir.resource.terminology.ValueSet response = new com.kodality.zmei.fhir.resource.terminology.ValueSet();
    response.setUrl(inlineVs.getUrl());
    response.setVersion(inlineVs.getVersion());
    response.setName(inlineVs.getName());
    response.setTitle(inlineVs.getTitle());
    response.setStatus(inlineVs.getStatus());
    response.setCompose(inlineVs.getCompose());

    // Build expansion
    com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion expansion = 
        new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion();
    expansion.setTimestamp(java.time.OffsetDateTime.now());
    expansion.setTotal(expandedConcepts.size());
    if (offset != null) {
      expansion.setOffset(offset);
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
          if (includeDesignations && concept.getAdditionalDesignations() != null) {
            contain.setDesignation(concept.getAdditionalDesignations().stream()
                .map(d -> {
                  com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConceptDesignation designation = 
                      new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConceptDesignation();
                  designation.setLanguage(d.getLanguage());
                  designation.setValue(d.getName());
                  return designation;
                }).toList());
          }
          return contain;
        }).toList();
    expansion.setContains(contains);

    response.setExpansion(expansion);
    return response;
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

    List<ValueSetVersionConcept> concepts = provider.ruleExpand(rule, stubVersion, displayLanguage);
    if (concepts == null) {
      concepts = Collections.emptyList();
    }

    // Filter (free-text typeahead) — applied client-side because the underlying
    // Snowstorm ECL doesn't carry a free-text filter.
    String textFilter = req == null ? null : req.findParameter("filter")
        .map(pp -> pp.getValueString() != null ? pp.getValueString() : pp.getValueCode()).orElse(null);
    if (textFilter != null && !textFilter.isBlank()) {
      String needle = textFilter.toLowerCase();
      concepts = concepts.stream().filter(c -> {
        String code = c.getConcept() != null ? c.getConcept().getCode() : null;
        String display = c.getDisplay() != null ? c.getDisplay().getName() : null;
        return (code != null && code.toLowerCase().contains(needle))
            || (display != null && display.toLowerCase().contains(needle));
      }).toList();
    }

    int totalBeforePaging = concepts.size();

    Integer offset = req == null ? null : req.findParameter("offset").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("offset").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));
    if (offset != null) {
      concepts = offset >= concepts.size() ? List.of() : concepts.subList(offset, concepts.size());
    }
    Integer count = req == null ? null : req.findParameter("count").map(ParametersParameter::getValueInteger)
        .orElse(req.findParameter("count").map(ParametersParameter::getValueString).map(Integer::valueOf).orElse(null));
    if (count != null) {
      concepts = concepts.stream().limit(count).toList();
    }

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
}
