package org.termx.terminology.terminology.valueset.expansion

import com.kodality.commons.model.QueryResult
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.core.ts.ValueSetExternalExpandProvider
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService
import org.termx.terminology.terminology.valueset.snapshot.ValueSetSnapshotService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionRepository
import org.termx.ts.codesystem.CodeSystemEntityVersion
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams
import org.termx.ts.codesystem.CodeSystemVersionReference
import org.termx.ts.codesystem.Designation
import org.termx.ts.codesystem.EntityProperty
import org.termx.ts.codesystem.EntityPropertyType
import org.termx.ts.codesystem.EntityPropertyValue
import org.termx.ts.valueset.ValueSetSnapshot

import java.time.LocalDate
import java.time.OffsetDateTime
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionConcept
import org.termx.ts.valueset.ValueSetVersionRuleSet
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import spock.lang.Specification

class ValueSetVersionConceptServiceTest extends Specification {
  def repository = Mock(ValueSetVersionConceptRepository)
  def valueSetVersionRepository = Mock(ValueSetVersionRepository)
  def valueSetSnapshotService = Mock(ValueSetSnapshotService)
  def codeSystemEntityVersionService = Mock(CodeSystemEntityVersionService)
  def entityPropertyService = Mock(EntityPropertyService)
  def codeSystemVersionResolver = Mock(ValueSetCodeSystemVersionResolver)

  def cleanup() {
    SessionStore.clearLocal()
  }

  def "expand returns external provider concepts with preferred language"() {
    given:
    repository.expand(1L) >> []
    codeSystemEntityVersionService.query(_) >> QueryResult.empty()
    entityPropertyService.query(_) >> QueryResult.empty()

    def service = new ValueSetVersionConceptService(
        [ucumProvider(vsConcept("mL", [designation("abbreviation", "lt", "ml")]))],
        repository,
        valueSetVersionRepository,
        valueSetSnapshotService,
        codeSystemEntityVersionService,
        entityPropertyService,
        codeSystemVersionResolver
    )

    when:
    def expanded = service.expand(valueSetVersion(), "lt", true)

    then:
    expanded.size() == 1
    expanded.first().additionalDesignations.find { it.language == "lt" && it.designationType == "abbreviation" }?.name == "ml"
  }

  def "expand bypasses active snapshot cache when includeDesignations is requested"() {
    given:
    repository.expand(1L) >> []
    codeSystemEntityVersionService.query(_) >> QueryResult.empty()
    entityPropertyService.query(_) >> QueryResult.empty()

    def service = new ValueSetVersionConceptService(
        [ucumProvider(vsConcept("mL", [designation("abbreviation", "lt", "ml")]))],
        repository,
        valueSetVersionRepository,
        valueSetSnapshotService,
        codeSystemEntityVersionService,
        entityPropertyService,
        codeSystemVersionResolver
    )
    def version = valueSetVersion()
        .setStatus("active")
        .setSnapshot(new ValueSetSnapshot().setExpansion([vsConcept("mL", [designation("display", "en", "milliliter")])]))

    when:
    def expanded = service.expand(version, null, true)

    then:
    expanded.first().additionalDesignations.find { it.language == "lt" && it.designationType == "abbreviation" }?.name == "ml"
  }

  def "reuses the stored snapshot for a published (retired) version without recomputing or overwriting"() {
    given:
    def snapshot = new ValueSetSnapshot().setExpansion([vsConcept("mL", [])])
    def version = valueSetVersion().setStatus("retired").setSnapshot(snapshot)
    valueSetVersionRepository.load("vs", "1.0.0") >> version
    codeSystemVersionResolver.isDynamic(_) >> false

    when:
    def result = newService().expand("vs", "1.0.0", null, false)

    then: "the frozen snapshot is returned as-is and never re-created"
    result.is(snapshot)
    0 * valueSetSnapshotService.createSnapshot(_, _, _, _)
  }

  def "(f) renders a displayLanguage from the snapshot without a DB query or rewrite when it is a VS language"() {
    given: "an active version that supports et/en with a snapshot carrying both displays"
    def concept = vsConcept("9021002", 100L,
        designation("display", "en", "Carbaryl"),
        [designation("display", "et", "karbarüül")])
    def version = valueSetVersion().setStatus("active").setSupportedLanguages(["et", "en"])
        .setSnapshot(new ValueSetSnapshot().setValueSet("vs").setExpansion([concept]))
    valueSetVersionRepository.load("vs", "1.0.0") >> version
    codeSystemVersionResolver.isDynamic(_) >> false

    when: "et is requested"
    def result = newService().expand("vs", "1.0.0", "et", false)

    then: "display is re-picked from the snapshot — no DB query, no rewrite"
    result.expansion.first().display.name == "karbarüül"
    0 * codeSystemEntityVersionService.query(_)
    0 * valueSetSnapshotService.createSnapshot(_, _, _, _)
  }

  def "(b) loads only designations for a non-VS language and does not rewrite the snapshot"() {
    given: "an active version that supports only en"
    def concept = vsConcept("9021002", 100L, designation("display", "en", "Carbaryl"), [])
    def version = valueSetVersion().setStatus("active").setSupportedLanguages(["en"])
        .setSnapshot(new ValueSetSnapshot().setValueSet("vs").setExpansion([concept]))
    valueSetVersionRepository.load("vs", "1.0.0") >> version
    codeSystemVersionResolver.isDynamic(_) >> false

    when: "et (not a VS language) is requested"
    def result = newService().expand("vs", "1.0.0", "et", false)

    then: "only the et display designation is loaded; concepts/snapshot untouched"
    1 * codeSystemEntityVersionService.query(_) >> new QueryResult([
        new CodeSystemEntityVersion().setId(100L).setDesignations([designation("display", "et", "karbarüül")])])
    result.expansion.first().display.name == "karbarüül"
    0 * valueSetSnapshotService.createSnapshot(_, _, _, _)
  }

  def "(c) persists the snapshot for a draft only when the caller has write permission"() {
    given:
    repository.expand(1L) >> []
    codeSystemEntityVersionService.query(_) >> QueryResult.empty()
    entityPropertyService.query(_) >> QueryResult.empty()
    SessionStore.setLocal(new SessionInfo().setPrivileges(["vs.ValueSet.write"] as Set))
    def version = valueSetVersion().setStatus("draft")
    valueSetVersionRepository.load("vs", "1.0.0") >> version
    codeSystemVersionResolver.isDynamic(_) >> false

    when:
    def result = newService().expand("vs", "1.0.0", null, false)

    then:
    1 * valueSetSnapshotService.createSnapshot("vs", 1L, _, _) >> { args -> new ValueSetSnapshot().setExpansion(args[2]) }
    result != null
  }

  def "(c) does not persist the snapshot when the caller lacks write permission"() {
    given:
    repository.expand(1L) >> []
    codeSystemEntityVersionService.query(_) >> QueryResult.empty()
    entityPropertyService.query(_) >> QueryResult.empty()
    def version = valueSetVersion().setStatus("draft")
    valueSetVersionRepository.load("vs", "1.0.0") >> version
    codeSystemVersionResolver.isDynamic(_) >> false

    when: "a read-only caller (no session) expands a draft"
    def result = newService().expand("vs", "1.0.0", null, false)

    then: "a transient expansion is returned and nothing is written"
    result != null
    result.expansion != null
    0 * valueSetSnapshotService.createSnapshot(_, _, _, _)
  }

  def "(#49) decorate scopes designations to the latest bound code system version"() {
    given: "a code that exists as a separate entity version in LOINC 2.80 and 2.81, each with its own designations"
    entityPropertyService.query(_) >> QueryResult.empty()
    def v280 = new CodeSystemEntityVersion().setId(80L).setCodeSystem("loinc").setCode("1234-5").setStatus("active")
        .setVersions([csVersionRef("2.80", LocalDate.of(2023, 1, 1))])
        .setDesignations([designation("display", "en", "name 2.80"), designation("abbreviation", "en", "ab80")])
    def v281 = new CodeSystemEntityVersion().setId(81L).setCodeSystem("loinc").setCode("1234-5").setStatus("active")
        .setVersions([csVersionRef("2.81", LocalDate.of(2024, 1, 1))])
        .setDesignations([designation("display", "en", "name 2.81"), designation("abbreviation", "en", "ab81")])
    codeSystemEntityVersionService.query(_) >> new QueryResult([v280, v281])

    and: "the expand returned both entity versions for the same code (cumulative version window)"
    def concepts = [loincConcept("1234-5", 80L), loincConcept("1234-5", 81L)]

    when:
    def result = newService().decorate(concepts, loincVersion(), "en")

    then: "only the newest (2.81) version's designations survive"
    result.size() == 1
    def c = result.first()
    c.display.name == "name 2.81"
    c.additionalDesignations*.name as Set == ["ab81"] as Set
    !(c.additionalDesignations*.name.contains("ab80"))

    and: "the concept still reports membership in every version it appears in"
    c.concept.codeSystemVersions as Set == ["2.80", "2.81"] as Set
  }

  def "(TLA-4) decorate collapses designations and properties when a code has several entity versions in one bound version"() {
    given: "a supplement code carrying two entity versions, both active members of the same (latest) code system version"
    entityPropertyService.query(_) >> new QueryResult([new EntityProperty().setName("effectiveDate")])
    def csVer = csVersionRef("20.0.0", LocalDate.of(2025, 5, 30))
    def older = new CodeSystemEntityVersion().setId(10L).setCodeSystem("supp").setCode("4221000146107").setStatus("active")
        .setCreated(OffsetDateTime.parse("2025-05-30T00:00:00Z"))
        .setVersions([csVer])
        .setDesignations([designation("description", "en", "Cryptococcus magnus (organism)")])
        .setPropertyValues([propertyValue("effectiveDate", "2017-11-23", 10L)])
    def newer = new CodeSystemEntityVersion().setId(11L).setCodeSystem("supp").setCode("4221000146107").setStatus("active")
        .setCreated(OffsetDateTime.parse("2026-05-29T00:00:00Z"))
        .setVersions([csVer])
        .setDesignations([designation("description", "en", "Cryptococcus magnus (organism)")])
        .setPropertyValues([propertyValue("effectiveDate", "2017-11-23", 11L)])
    codeSystemEntityVersionService.query(_) >> new QueryResult([older, newer])

    and: "the expand returned both entity versions for the same code"
    def concepts = [suppConcept("4221000146107", 10L), suppConcept("4221000146107", 11L)]

    when:
    def result = newService().decorate(concepts, suppVersion(), "en")

    then: "the code appears once and neither its designation nor its property value is multiplied"
    result.size() == 1
    def c = result.first()
    c.additionalDesignations.findAll { it.designationType == "description" && it.language == "en" }.size() == 1
    c.propertyValues.findAll { it.entityProperty == "effectiveDate" }.size() == 1
  }

  def "(TLA-4) currentEntityVersion keeps the newest entity version sharing the latest code system version"() {
    given: "two entity versions of the same code, both bound to the same code system version"
    def csVer = csVersionRef("20.0.0", LocalDate.of(2025, 5, 30))
    def older = new CodeSystemEntityVersion().setId(10L).setCreated(OffsetDateTime.parse("2025-05-30T00:00:00Z")).setVersions([csVer])
    def newer = new CodeSystemEntityVersion().setId(11L).setCreated(OffsetDateTime.parse("2026-05-29T00:00:00Z")).setVersions([csVer])

    expect: "only the newest survives (by created, then id), and empty/single inputs pass through"
    ValueSetVersionConceptService.currentEntityVersion([older, newer])*.id == [11L]
    ValueSetVersionConceptService.currentEntityVersion([newer]) == [newer]
    ValueSetVersionConceptService.currentEntityVersion([]) == []
  }

  def "(#49) latestCodeSystemVersion keeps a single version and treats an unreleased version as newest"() {
    expect:
    ValueSetVersionConceptService.latestCodeSystemVersion([]) == []
    ValueSetVersionConceptService.latestCodeSystemVersion(null) == []

    when: "a released version competes with an unreleased (null release date) one"
    def released = new CodeSystemEntityVersion().setId(1L).setVersions([csVersionRef("1.0", LocalDate.of(2024, 1, 1))])
    def unreleased = new CodeSystemEntityVersion().setId(2L).setVersions([csVersionRef("2.0", null)])
    def kept = ValueSetVersionConceptService.latestCodeSystemVersion([released, unreleased])

    then: "the unreleased one wins (draft = newest), matching the expand SQL ordering"
    kept*.id == [2L]
  }

  def "(#36) expansion queries entity versions with base-code-system fan-out disabled"() {
    given:
    entityPropertyService.query(_) >> QueryResult.empty()
    CodeSystemEntityVersionQueryParams captured = null

    when:
    newService().decorate([loincConcept("1234-5", 80L)], loincVersion(), "en")

    then: "the per-concept base/dependent code system designation+property fan-out is switched off (issue #36)"
    1 * codeSystemEntityVersionService.query(_) >> { args -> captured = args[0] as CodeSystemEntityVersionQueryParams; QueryResult.empty() }
    captured != null
    !captured.decorateBaseCodeSystem
  }

  private static ValueSetVersionConcept loincConcept(String code, Long conceptVersionId) {
    return new ValueSetVersionConcept()
        .setConcept(new ValueSetVersionConcept.ValueSetVersionConceptValue()
            .setCode(code)
            .setConceptVersionId(conceptVersionId)
            .setCodeSystem("loinc")
            .setCodeSystemUri("http://loinc.org"))
        .setActive(true)
  }

  private static ValueSetVersionConcept suppConcept(String code, Long conceptVersionId) {
    return new ValueSetVersionConcept()
        .setConcept(new ValueSetVersionConcept.ValueSetVersionConceptValue()
            .setCode(code)
            .setConceptVersionId(conceptVersionId)
            .setCodeSystem("supp")
            .setCodeSystemUri("http://example.org/CodeSystem/supp"))
        .setActive(true)
  }

  private static ValueSetVersion suppVersion() {
    return new ValueSetVersion()
        .setId(1L)
        .setStatus("draft")
        .setRuleSet(new ValueSetVersionRuleSet()
            .setInactive(false)
            .setRules([new ValueSetVersionRule().setType("include").setCodeSystem("supp")]))
  }

  private static EntityPropertyValue propertyValue(String name, Object value, Long codeSystemEntityVersionId) {
    return new EntityPropertyValue()
        .setEntityProperty(name)
        .setValue(value)
        .setEntityPropertyType(EntityPropertyType.string)
        .setCodeSystemEntityVersionId(codeSystemEntityVersionId)
  }

  private static ValueSetVersion loincVersion() {
    return new ValueSetVersion()
        .setId(1L)
        .setStatus("draft")
        .setRuleSet(new ValueSetVersionRuleSet()
            .setInactive(false)
            .setRules([new ValueSetVersionRule().setType("include").setCodeSystem("loinc")]))
  }

  private static CodeSystemVersionReference csVersionRef(String version, LocalDate releaseDate) {
    return new CodeSystemVersionReference().setVersion(version).setReleaseDate(releaseDate)
  }

  private ValueSetVersionConceptService newService() {
    return new ValueSetVersionConceptService(
        [ucumProvider(vsConcept("mL", [designation("display", "et", "milliliiter")]))],
        repository,
        valueSetVersionRepository,
        valueSetSnapshotService,
        codeSystemEntityVersionService,
        entityPropertyService,
        codeSystemVersionResolver
    )
  }

  private static ValueSetVersion valueSetVersion() {
    return new ValueSetVersion()
        .setId(1L)
        .setStatus("draft")
        .setRuleSet(new ValueSetVersionRuleSet()
            .setInactive(false)
            .setRules([new ValueSetVersionRule().setType("include").setCodeSystem("ucum")]))
  }

  private static ValueSetVersionConcept vsConcept(String code, List<Designation> designations) {
    return new ValueSetVersionConcept()
        .setConcept(new ValueSetVersionConcept.ValueSetVersionConceptValue()
            .setCode(code)
            .setCodeSystem("ucum")
            .setCodeSystemUri("http://unitsofmeasure.org"))
        .setAdditionalDesignations(designations)
        .setActive(true)
  }

  private static ValueSetVersionConcept vsConcept(String code, Long conceptVersionId, Designation display, List<Designation> additionalDesignations) {
    return new ValueSetVersionConcept()
        .setConcept(new ValueSetVersionConcept.ValueSetVersionConceptValue()
            .setCode(code)
            .setConceptVersionId(conceptVersionId)
            .setCodeSystem("ucum")
            .setCodeSystemUri("http://unitsofmeasure.org"))
        .setDisplay(display)
        .setAdditionalDesignations(additionalDesignations)
        .setActive(true)
  }

  private static Designation designation(String type, String language, String value) {
    return new Designation()
        .setDesignationType(type)
        .setLanguage(language)
        .setName(value)
  }

  private static ValueSetExternalExpandProvider ucumProvider(ValueSetVersionConcept concept) {
    return new ValueSetExternalExpandProvider() {
      @Override
      List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule, ValueSetVersion version, String preferredLanguage) {
        return [concept]
      }

      @Override
      String getCodeSystemId() {
        return "ucum"
      }
    }
  }
}
