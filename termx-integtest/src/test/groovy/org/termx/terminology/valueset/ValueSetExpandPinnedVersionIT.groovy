package org.termx.terminology.valueset

import java.util.Collections
import com.kodality.commons.model.LocalizedName
import com.kodality.zmei.fhir.FhirMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper
import org.termx.terminology.terminology.codesystem.CodeSystemImportService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptRepository
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.PublicationStatus
import org.termx.ts.codesystem.CodeSystemImportAction
import org.termx.ts.codesystem.CodeSystemVersionReference
import org.termx.ts.property.PropertyReference
import org.termx.ts.valueset.ValueSet
import org.termx.ts.valueset.ValueSetTransactionRequest
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionRuleSet
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule

import java.time.LocalDate

/**
 * A ValueSet rule pinned to a specific CodeSystem version must expand to ONLY that version's members.
 * The stored-version path {@code terminology.value_set_expand(bigint)} ({@code 01-value_set_expand.sql})
 * is strict per-version: a concept that is NOT a member of the pinned version is not returned, even if
 * it was a member of an earlier version.
 *
 * <p>Historically fn01 carried a cumulative {@code release_date <=} fallback (added in {@code 5af30777}
 * "TEHIK fixes" for delta-versioned code systems) that pulled earlier-version concepts forward. It was
 * removed: FHIR has no cross-version delta semantics ({@code include.version = X} means X's content
 * as-is), so a version that ships only deltas must be normalised to a full release at import time, not
 * reconstructed at expansion time.
 *
 * <p>Fixture: code system {@code vsexpand-pinned-cs} imported as two full releases —
 * <pre>
 *   1.0.0 : [keep, drop]
 *   2.0.0 : [keep]            // 'drop' absent from 2.0.0, still ACTIVE in 1.0.0 (not retired)
 * </pre>
 * A ValueSet pinned to 2.0.0 (all concepts) must expand to [keep], not [keep, drop].
 */
@MicronautTest(transactional = true)
class ValueSetExpandPinnedVersionIT extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper fhirMapper
  @Inject CodeSystemVersionService codeSystemVersionService
  @Inject ValueSetService valueSetService
  @Inject ValueSetVersionService valueSetVersionService
  @Inject ValueSetVersionConceptRepository conceptRepository

  static final String CS_ID = "vsexpand-pinned-cs"
  static final String CS_URI = "http://termx-test.local/CodeSystem/vsexpand-pinned-cs"
  static final String VS_ID = "vsexpand-pinned-vs"
  static final String VS_VERSION = "1.0.0"

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    // Distinct release dates (1.0.0 before 2.0.0): the cumulative fallback keys on
    // 'release_date <= pinned version release_date', so the dates must be set for it to fire.
    importCs("1.0.0", ["keep", "drop"], LocalDate.of(2026, 1, 1))
    // import 2.0.0 WITHOUT cleanRun so 'drop' is not retired — it stays an active member of 1.0.0
    // only and is simply absent from 2.0.0's membership (this shape).
    importCs("2.0.0", ["keep"], LocalDate.of(2026, 2, 1))
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "expansion pinned to a CodeSystem version returns only that version's members"() {
    expect:
    expandPinnedTo("2.0.0") == ["keep"] as Set
  }

  def "sanity: expansion pinned to the older version returns its full member set"() {
    expect:
    expandPinnedTo("1.0.0") == ["keep", "drop"] as Set
  }

  // --- helpers ---------------------------------------------------------------

  private Set<String> expandPinnedTo(String codeSystemVersion) {
    def versionId = codeSystemVersionService.load(CS_ID, codeSystemVersion).orElseThrow().getId()
    // Use a property FILTER (grp = x), not an all-concepts rule: in 01-value_set_expand.sql only the
    // filter/hierarchy CTEs carry the cumulative release_date<= fallback (the all-concepts cs_all CTE
    // is already strict), and the affected (customer) value set is filter-based.
    def rule = new ValueSetVersionRule()
        .setType("include")
        .setCodeSystem(CS_ID)
        .setCodeSystemVersion(new CodeSystemVersionReference().setId(versionId).setVersion(codeSystemVersion))
        .setFilters(Collections.unmodifiableList([new ValueSetVersionRule.ValueSetRuleFilter()
            .setProperty(new PropertyReference().setName("grp")).setOperator("=").setValue("x")]))

    def version = new ValueSetVersion()
        .setStatus(PublicationStatus.draft)
        .setRuleSet(new ValueSetVersionRuleSet().setRules([rule]))
    version.setValueSet(VS_ID)
    version.setVersion(VS_VERSION)

    def request = new ValueSetTransactionRequest()
    request.setValueSet(new ValueSet().setId(VS_ID).setUri("http://termx-test.local/ValueSet/" + VS_ID)
        .setTitle(new LocalizedName().add("en", "Pinned version expansion")))
    request.setVersion(version)
    valueSetService.save(request)

    def vsVersionId = valueSetVersionService.load(VS_ID, VS_VERSION).orElseThrow().getId()
    return conceptRepository.expand(vsVersionId).collect { it.concept?.code }.findAll { it != null } as Set
  }

  @SuppressWarnings("EI_EXPOSE_REP2")
  private void importCs(String version, List<String> codes, LocalDate releaseDate) {
    def concepts = codes.collect {
      "{\"code\": \"${it}\", \"display\": \"${it}\", \"property\": [{\"code\": \"grp\", \"valueString\": \"x\"}]}"
    }.join(",")
    def json = """
      {
        "resourceType": "CodeSystem",
        "url": "${CS_URI}",
        "version": "${version}",
        "status": "active",
        "content": "complete",
        "property": [{"code": "grp", "type": "string"}],
        "concept": [${concepts}]
      }
    """
    def fhir = FhirMapper.fromJson(json, com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    def cs = fhirMapper.fromFhirCodeSystem(fhir)
    cs.versions.each { it.setReleaseDate(releaseDate) }
    importService.importCodeSystem(cs, [], new CodeSystemImportAction().setActivate(true).setCleanRun(false).setCleanConceptRun(false))
  }
}
