package org.termx.terminology.fhir.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import org.termx.core.fhir.BaseFhirResourceHandler;
import org.termx.core.sys.provenance.Provenance;
import org.termx.core.sys.provenance.ProvenanceService;
import org.termx.terminology.terminology.codesystem.CodeSystemImportService;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.ts.PublicationStatus;
import org.termx.ts.association.AssociationKind;
import org.termx.ts.association.AssociationType;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import org.termx.ts.codesystem.CodeSystemImportAction;
import org.termx.ts.codesystem.CodeSystemQueryParams;
import org.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.zmei.fhir.FhirMapper;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Singleton
public class CodeSystemResourceStorage extends BaseFhirResourceHandler {
  /**
   * Absolute ceiling for inline-loading a CodeSystem version's concept entities into a single
   * FHIR response body. Above this size the on-wire JSON would be unworkable for any client
   * and the JDBC + heap cost has been observed to OOM dev-server with 1-2 GB heap on real
   * publishers (LOINC ~100k, ICD-10 ~70k, SNOMED CT ~370k). When exceeded, {@link #loadEntities}
   * returns an empty list and logs a WARN — clients should use {@code ?_summary=true},
   * {@code /$expand}, or the concept-pagination endpoint instead. The {@code count} field on
   * the response stays accurate because it comes from {@code CodeSystemVersion.conceptsTotal}
   * (a single COUNT query inside versionService.load), not from {@code entities.size()}.
   * Override via {@code termx.fhir.codesystem.read.max-inline-entities}.
   */
  private final int maxInlineEntities;

  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final ProvenanceService provenanceService;
  private final CodeSystemImportService importService;
  private final CodeSystemFhirMapper mapper;
  private final String defaultSearchSummary;

  public CodeSystemResourceStorage(CodeSystemService codeSystemService,
                                   CodeSystemVersionService codeSystemVersionService,
                                   CodeSystemEntityVersionService codeSystemEntityVersionService,
                                   ProvenanceService provenanceService,
                                   CodeSystemImportService importService,
                                   CodeSystemFhirMapper mapper,
                                   @Value("${termx.fhir.codesystem.search.default-summary:true}") String defaultSearchSummary,
                                   @Value("${termx.fhir.codesystem.read.max-inline-entities:50000}") int maxInlineEntities) {
    this.codeSystemService = codeSystemService;
    this.codeSystemVersionService = codeSystemVersionService;
    this.codeSystemEntityVersionService = codeSystemEntityVersionService;
    this.provenanceService = provenanceService;
    this.importService = importService;
    this.mapper = mapper;
    this.defaultSearchSummary = defaultSearchSummary;
    this.maxInlineEntities = maxInlineEntities;
  }

  @Override
  public String getResourceType() {
    return "CodeSystem";
  }

  @Override
  public String getPrivilegeName() {
    return "CodeSystem";
  }

  @Override
  public ResourceVersion load(String fhirId) {
    String[] idParts = CodeSystemFhirMapper.parseCompositeId(fhirId);
    return load(idParts[0], idParts[1]);
  }

  private ResourceVersion load(String codeSystemId, String versionNumber) {
    CodeSystemQueryParams codeSystemParams = new CodeSystemQueryParams();
    codeSystemParams.setId(codeSystemId);
    codeSystemParams.setPropertiesDecorated(true);
    codeSystemParams.setLimit(1);
    CodeSystem codeSystem = codeSystemService.query(codeSystemParams).findFirst().orElse(null);
    if (codeSystem == null) {
      return null;
    }

    long start = System.currentTimeMillis();
    CodeSystemVersion version = versionNumber == null ? codeSystemVersionService.loadLastVersion(codeSystemId) :
        codeSystemVersionService.load(codeSystemId, versionNumber).orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "resource not found"));
    log.info("Code System load took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
    start = System.currentTimeMillis();

    // Honour ?_summary on read: when the client asked for a lightweight summary (true /
    // text / count) we skip the concept-entity load entirely. The post-load summary
    // processor in kefhir would strip the `concept` array from the response anyway, so
    // we'd otherwise be loading ~100 000 rows + designations + property values + JSON-
    // serialising them just to throw them away.
    //
    // The `count` field on the response is still populated — it comes from
    // CodeSystemVersion.conceptsTotal (single COUNT query inside versionService.load),
    // not from entities.size() — see CodeSystemFhirMapper.toFhir's setCount() line.
    //
    // Default for read (no _summary param) is to load everything, matching the previous
    // behaviour and the FHIR R5 spec default of _summary=false on read/vread.
    boolean lightweight = isCurrentRequestLightweightSummary();
    version.setEntities(lightweight ? List.of() : loadEntities(version, null, true));
    log.info("Entities load took {} seconds (lightweight={})", (System.currentTimeMillis() - start) / 1000, lightweight);
    start = System.currentTimeMillis();

    ResourceVersion resourceVersion = toFhir(codeSystem, version);
    log.info("To FHIR conversion took " + (System.currentTimeMillis() - start) / 1000 + " seconds");

    return resourceVersion;
  }

  @Override
  public ResourceVersion save(ResourceId id, ResourceContent content) {
    com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem =
        FhirMapper.fromJson(content.getValue(), com.kodality.zmei.fhir.resource.terminology.CodeSystem.class);
    if (codeSystem.getId() != null) {
      String[] idParts = CodeSystemFhirMapper.parseCompositeId(codeSystem.getId());
      codeSystem.setId(idParts[0]);
      codeSystem.setVersion(Optional.ofNullable(idParts[1]).orElse(codeSystem.getVersion()));
    }
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true));
    CodeSystemImportAction action = new CodeSystemImportAction().setActivate(PublicationStatus.active.equals(codeSystem.getStatus()));
    CodeSystem cs = importService.importCodeSystem(mapper.fromFhirCodeSystem(codeSystem), associationTypes, action);
    return load(cs.getId(), cs.getVersions().getFirst().getVersion());
  }

  @Override
  public String generateNewId() {
    return null; //TODO:
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    QueryResult<CodeSystem> csResult = codeSystemService.query(CodeSystemFhirMapper.fromFhir(criteria));
    QueryResult<CodeSystemVersion> csvResult = codeSystemVersionService.query(CodeSystemFhirMapper.fromFhirCSVersionParams(criteria).limit(0));
    String code = criteria.getRawParams().containsKey("code") ? criteria.getRawParams().get("code").get(0) : null;
    boolean lightweight = isLightweightSummary(criteria.getSummary() != null ? criteria.getSummary() : defaultSearchSummary);
    return new SearchResult(csvResult.getMeta().getTotal(), csResult.getData().stream().flatMap(cs -> cs.getVersions().stream().map(csv -> {
      csv.setEntities(lightweight ? List.of() : loadEntities(csv, code, false));
      return toFhir(cs, csv);
    })).toList());
  }

  private static boolean isLightweightSummary(String summary) {
    return summary != null && !summary.isBlank() && !"false".equalsIgnoreCase(summary);
  }

  private List<CodeSystemEntityVersion> loadEntities(CodeSystemVersion version, String code, boolean loadLargeEntities) {
    if (version == null) {
      return List.of();
    }
    Integer total = version.getConceptsTotal();
    if (total != null && total > 1000 && !loadLargeEntities) {
      // Search path: callers that don't ask for the heavy load skip it for anything above 1k.
      return List.of();
    }
    // Hard ceiling: even on the read path with loadLargeEntities=true, refuse to inline
    // huge concept sets. The previous behaviour pulled every row into one JDBC result set,
    // OOM-ing the JVM on real publishers (LOINC, ICD-10, SNOMED CT). The CodeSystem still
    // serialises with an accurate `count`; clients that need the concepts should use
    // ?_summary=true, /$expand, or the concept-pagination endpoint.
    if (code == null && total != null && total > maxInlineEntities) {
      log.warn("CodeSystem {} version {} has {} concepts, exceeding inline cap {} — returning "
              + "without entities. Clients should call /$expand or paginate /concept instead, "
              + "or override termx.fhir.codesystem.read.max-inline-entities for this server.",
          version.getCodeSystem(), version.getVersion(), total, maxInlineEntities);
      return List.of();
    }
    CodeSystemEntityVersionQueryParams codeSystemEntityVersionParams = new CodeSystemEntityVersionQueryParams()
        .setCodeSystemVersionId(version.getId())
        .setCode(code)
        .all();
    return codeSystemEntityVersionService.query(codeSystemEntityVersionParams).getData();
  }

  private ResourceVersion toFhir(CodeSystem cs, CodeSystemVersion csv) {
    List<Provenance> provenances = provenanceService.find("CodeSystemVersion|" + csv.getId());
    return cs == null ? null : new ResourceVersion(
        new VersionId("CodeSystem", CodeSystemFhirMapper.toFhirId(cs, csv)),
        new ResourceContent(mapper.toFhirJson(cs, csv, provenances), "json")
    );
  }

}
