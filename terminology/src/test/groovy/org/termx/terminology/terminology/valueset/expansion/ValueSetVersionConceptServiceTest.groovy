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
import org.termx.ts.codesystem.Designation
import org.termx.ts.valueset.ValueSetSnapshot
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
