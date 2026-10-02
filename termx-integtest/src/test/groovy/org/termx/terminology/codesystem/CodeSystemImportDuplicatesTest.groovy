package org.termx.terminology.codesystem

import com.kodality.zmei.fhir.FhirMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.springframework.jdbc.core.JdbcTemplate
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper
import org.termx.terminology.terminology.codesystem.CodeSystemImportService
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams
import org.termx.ts.codesystem.CodeSystemImportAction

import javax.sql.DataSource

/**
 * Re-imports into the same code system version must not leave duplicated lines:
 * <ul>
 *   <li>a concept must be linked to the version by ONE of its entity versions — "Hold + Replace" on a
 *       changed active concept linked the new version beside the active one;</li>
 *   <li>a property value must be stored once — "Merge" compared values by their raw JSON, so a stored
 *       Coding that the enrichment job had given a display/version never matched the file's bare one,
 *       and every later Merge import added another copy (and copies already stored were kept forever).</li>
 * </ul>
 * Reported by a customer importing a 7,443-concept nomenclature repeatedly into one version.
 */
@MicronautTest(transactional = true)
class CodeSystemImportDuplicatesTest extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper fhirMapper
  @Inject CodeSystemEntityVersionService entityVersionService
  @Inject @Named("default") DataSource dataSource
  JdbcTemplate jdbc

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    jdbc = new JdbcTemplate(dataSource)
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "Hold + Replace on a changed ACTIVE concept leaves one version of it in the code system version"() {
    given:
    def cs = "dup-replace-active"
    def json = fixtureFor(cs)
    importService.importCodeSystem(domainCs(json), [], mergeAction())
    entityVersionService.activate(cs, new ArrayList<>(versionIds(cs, "a")))

    when: "concept 'a' changes and the file is imported with Hold + Replace"
    importService.importCodeSystem(domainCs(json.replace('"valueDecimal": 1.5', '"valueDecimal": 2.5')), [], replaceAction())

    then: "the version holds 'a' once — the new version, not the new one beside the old"
    memberCount(cs, "a") == 1
    memberCount(cs, "b") == 1
  }

  def "Merge after enrichment does not add another copy of a Coding value"() {
    given:
    def cs = "dup-merge-enriched"
    def json = fixtureFor(cs)
    importService.importCodeSystem(domainCs(json), [], mergeAction())
    enrich(cs, "a")

    when: "concept 'a' changes elsewhere and the same specimen is merged again"
    importService.importCodeSystem(domainCs(json.replace('"valueDecimal": 1.5', '"valueDecimal": 2.5')), [], mergeAction())

    then: "S is stored once"
    specimenRows(cs, "a") == 1
    memberCount(cs, "a") == 1
  }

  def "Merge collapses copies already stored by earlier imports"() {
    given:
    def cs = "dup-merge-repair"
    def json = fixtureFor(cs)
    importService.importCodeSystem(domainCs(json), [], mergeAction())
    duplicateSpecimen(cs, "a")
    assert specimenRows(cs, "a") == 2

    when:
    importService.importCodeSystem(domainCs(json.replace('"valueDecimal": 1.5', '"valueDecimal": 2.5')), [], mergeAction())

    then:
    specimenRows(cs, "a") == 1
  }

  def "a version that already links two versions of a concept is repaired by the next import"() {
    given: "'a' active in 1.0.0, then a second (older) version of it linked into 1.0.0 as an earlier import left it"
    def cs = "dup-stray-member"
    def json = fixtureFor(cs)
    importService.importCodeSystem(domainCs(json), [], mergeAction())
    entityVersionService.activate(cs, new ArrayList<>(versionIds(cs, "a")))
    importService.importCodeSystem(domainCs(json.replace('"valueDecimal": 1.5', '"valueDecimal": 2.5')), [], mergeAction())
    linkAllVersions(cs, "a")
    assert memberCount(cs, "a") == 2

    when: "the same file is imported again (merge — 'a' is unchanged)"
    importService.importCodeSystem(domainCs(json.replace('"valueDecimal": 1.5', '"valueDecimal": 2.5')), [], mergeAction())

    then:
    memberCount(cs, "a") == 1
  }

  // --- helpers ---------------------------------------------------------------------------------------

  private org.termx.ts.codesystem.CodeSystem domainCs(String json) {
    def fhir = FhirMapper.fromJson(json, com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    return fhirMapper.fromFhirCodeSystem(fhir)
  }

  private String fixtureFor(String csId) {
    def stream = getClass().getClassLoader().getResourceAsStream("fhir/duplicates/cs.json")
    def json = new String(stream.readAllBytes(), "UTF-8")
    return json.replace('"id": "dup-cs"', '"id": "' + csId + '"').replace('/dup-cs"', '/' + csId + '"')
  }

  private static CodeSystemImportAction mergeAction() {
    return new CodeSystemImportAction().setActivate(false).setCleanRun(false).setCleanConceptRun(false)
  }

  private static CodeSystemImportAction replaceAction() {
    return new CodeSystemImportAction().setActivate(false).setCleanRun(false).setCleanConceptRun(true)
  }

  private Set<Long> versionIds(String csId, String code) {
    def params = new CodeSystemEntityVersionQueryParams()
    params.setCodeSystem(csId)
    params.setCode(code)
    params.setStatus("active,draft,retired")
    params.all()
    return entityVersionService.query(params).getData().collect { it.id } as Set
  }

  /** How many of the concept's entity versions the code system version 1.0.0 links (the "lines" a reader sees). */
  private int memberCount(String csId, String code) {
    return jdbc.queryForObject("""
        select count(*) from terminology.entity_version_code_system_version_membership m
          join terminology.code_system_entity_version v on v.id = m.code_system_entity_version_id and v.sys_status = 'A'
          join terminology.code_system_version csv on csv.id = m.code_system_version_id and csv.sys_status = 'A'
         where m.sys_status = 'A' and v.code_system = ? and v.code = ? and csv.version = '1.0.0'""", Integer, csId, code)
  }

  /** Active 'specimen' rows on the concept's member versions. */
  private int specimenRows(String csId, String code) {
    return jdbc.queryForObject("""
        select count(*) from terminology.entity_property_value pv
          join terminology.entity_property ep on ep.id = pv.entity_property_id and ep.name = 'specimen'
          join terminology.entity_version_code_system_version_membership m on m.code_system_entity_version_id = pv.code_system_entity_version_id and m.sys_status = 'A'
          join terminology.code_system_entity_version v on v.id = pv.code_system_entity_version_id and v.sys_status = 'A'
         where pv.sys_status = 'A' and v.code_system = ? and v.code = ?""", Integer, csId, code)
  }

  /** What the Coding enrichment job does to a stored value: add display, version and targetEffectiveTime. */
  private void enrich(String csId, String code) {
    jdbc.update("""
        update terminology.entity_property_value pv
           set value = pv.value || '{"display":[{"name":"Serum","language":"en"}],"version":"1.0.0","targetEffectiveTime":"2026-01-01"}'::jsonb
          from terminology.entity_property ep, terminology.code_system_entity_version v
         where ep.id = pv.entity_property_id and ep.name = 'specimen' and v.id = pv.code_system_entity_version_id
           and v.code_system = ? and v.code = ? and pv.sys_status = 'A'""", csId, code)
  }

  /** Link every version of the concept into 1.0.0 — the state a faulty earlier import left. */
  private void linkAllVersions(String csId, String code) {
    jdbc.update("""
        insert into terminology.entity_version_code_system_version_membership (code_system_entity_version_id, code_system_version_id)
        select v.id, csv.id from terminology.code_system_entity_version v
          join terminology.code_system_version csv on csv.code_system = v.code_system and csv.version = '1.0.0' and csv.sys_status = 'A'
         where v.code_system = ? and v.code = ? and v.sys_status = 'A'
           and not exists (select 1 from terminology.entity_version_code_system_version_membership m
                            where m.code_system_entity_version_id = v.id and m.code_system_version_id = csv.id and m.sys_status = 'A')""",
        csId, code)
  }

  /** A second stored copy of the concept's specimen value, as earlier Merge imports left them. */
  private void duplicateSpecimen(String csId, String code) {
    jdbc.update("""
        insert into terminology.entity_property_value (entity_property_id, code_system_entity_version_id, value)
        select pv.entity_property_id, pv.code_system_entity_version_id, pv.value || '{"version":"1.0.0"}'::jsonb
          from terminology.entity_property_value pv
          join terminology.entity_property ep on ep.id = pv.entity_property_id and ep.name = 'specimen'
          join terminology.code_system_entity_version v on v.id = pv.code_system_entity_version_id
         where v.code_system = ? and v.code = ? and pv.sys_status = 'A'""", csId, code)
  }
}
