package org.termx.terminology.terminology.valueset.expansion

import com.kodality.commons.model.QueryResult
import org.termx.core.ts.ValueSetExternalExpandProvider
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService
import org.termx.terminology.terminology.valueset.snapshot.ValueSetSnapshotService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionRepository
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
