package com.kodality.termx.terminology.terminology.valueset.expansion

import com.kodality.commons.model.QueryResult
import com.kodality.termx.core.ts.ValueSetExternalExpandProvider
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import com.kodality.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService
import com.kodality.termx.terminology.terminology.valueset.snapshot.ValueSetSnapshotService
import com.kodality.termx.terminology.terminology.valueset.version.ValueSetVersionRepository
import com.kodality.termx.ts.codesystem.CodeSystem
import com.kodality.termx.ts.codesystem.CodeSystemContent
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion
import com.kodality.termx.ts.codesystem.Concept
import com.kodality.termx.ts.codesystem.Designation
import com.kodality.termx.ts.valueset.ValueSetSnapshot
import com.kodality.termx.ts.valueset.ValueSetVersion
import com.kodality.termx.ts.valueset.ValueSetVersionConcept
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import spock.lang.Specification

class ValueSetVersionConceptServiceTest extends Specification {
  def repository = Mock(ValueSetVersionConceptRepository)
  def valueSetVersionRepository = Mock(ValueSetVersionRepository)
  def valueSetSnapshotService = Mock(ValueSetSnapshotService)
  def codeSystemEntityVersionService = Mock(CodeSystemEntityVersionService)
  def entityPropertyService = Mock(EntityPropertyService)
  def codeSystemService = Mock(CodeSystemService)
  def conceptService = Mock(ConceptService)

  def "expand enriches UCUM external concepts with supplement designations for preferred language"() {
    given:
    repository.expand(1L) >> []
    codeSystemEntityVersionService.query(_) >> QueryResult.empty()
    entityPropertyService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> new QueryResult([
        new CodeSystem().setId("ucum-supplement-lt").setBaseCodeSystem("ucum").setContent(CodeSystemContent.supplement)
    ])
    conceptService.query(_) >> new QueryResult([
        new Concept()
            .setCode("mL")
            .setVersions([new CodeSystemEntityVersion().setDesignations([
                designation("abbreviation", "lt", "ml"),
                designation("definition", "lt", "Milliliter")
            ])])
    ])

    def service = new ValueSetVersionConceptService(
        [ucumProvider(vsConcept("mL", [designation("display", "en", "milliliter")]))],
        repository,
        valueSetVersionRepository,
        valueSetSnapshotService,
        codeSystemEntityVersionService,
        entityPropertyService,
        codeSystemService,
        conceptService
    )

    when:
    def expanded = service.expand(valueSetVersion(), "lt")
    def designations = expanded.first().additionalDesignations

    then:
    expanded.size() == 1
    designations.find { it.language == "lt" && it.designationType == "abbreviation" }?.name == "ml"
    designations.find { it.language == "lt" && it.designationType == "definition" }?.name == "Milliliter"
  }

  def "expand bypasses active snapshot cache when preferred language is requested"() {
    given:
    repository.expand(1L) >> []
    codeSystemEntityVersionService.query(_) >> QueryResult.empty()
    entityPropertyService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> new QueryResult([
        new CodeSystem().setId("ucum-supplement-lt").setBaseCodeSystem("ucum").setContent(CodeSystemContent.supplement)
    ])
    conceptService.query(_) >> new QueryResult([
        new Concept()
            .setCode("mL")
            .setVersions([new CodeSystemEntityVersion().setDesignations([
                designation("abbreviation", "lt", "ml")
            ])])
    ])

    def service = new ValueSetVersionConceptService(
        [ucumProvider(vsConcept("mL", [designation("display", "en", "milliliter")]))],
        repository,
        valueSetVersionRepository,
        valueSetSnapshotService,
        codeSystemEntityVersionService,
        entityPropertyService,
        codeSystemService,
        conceptService
    )
    def version = valueSetVersion()
        .setStatus("active")
        .setSnapshot(new ValueSetSnapshot().setExpansion([vsConcept("mL", [designation("display", "en", "milliliter")])]))

    when:
    def expanded = service.expand(version, "lt")

    then:
    expanded.first().additionalDesignations.find { it.language == "lt" && it.designationType == "abbreviation" }?.name == "ml"
  }

  def "expand enriches internal enumerated UCUM concepts with supplement designations"() {
    given:
    repository.expand(1L) >> [vsConcept("mL", [designation("display", "en", "milliliter")]).setEnumerated(true)]
    codeSystemEntityVersionService.query(_) >> QueryResult.empty()
    entityPropertyService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> new QueryResult([
        new CodeSystem().setId("ucum-supplement-lt").setBaseCodeSystem("ucum").setContent(CodeSystemContent.supplement)
    ])
    conceptService.query(_) >> new QueryResult([
        new Concept()
            .setCode("mL")
            .setVersions([new CodeSystemEntityVersion().setDesignations([
                designation("abbreviation", "lt", "ml")
            ])])
    ])

    def service = new ValueSetVersionConceptService(
        [],
        repository,
        valueSetVersionRepository,
        valueSetSnapshotService,
        codeSystemEntityVersionService,
        entityPropertyService,
        codeSystemService,
        conceptService
    )

    when:
    def expanded = service.expand(valueSetVersion(), "lt")

    then:
    expanded.size() == 1
    expanded.first().additionalDesignations.find { it.language == "lt" && it.designationType == "abbreviation" }?.name == "ml"
  }

  def "expand discovers UCUM supplements by baseCodeSystemUri"() {
    given:
    repository.expand(1L) >> [vsConcept("mL", [designation("display", "en", "milliliter")]).setEnumerated(true)]
    codeSystemEntityVersionService.query(_) >> QueryResult.empty()
    entityPropertyService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> new QueryResult([
        new CodeSystem().setId("ucum-supplement-lt").setBaseCodeSystemUri("http://unitsofmeasure.org").setContent(CodeSystemContent.supplement)
    ])
    conceptService.query(_) >> new QueryResult([
        new Concept()
            .setCode("mL")
            .setVersions([new CodeSystemEntityVersion().setDesignations([
                designation("abbreviation", "lt", "ml")
            ])])
    ])

    def service = new ValueSetVersionConceptService(
        [],
        repository,
        valueSetVersionRepository,
        valueSetSnapshotService,
        codeSystemEntityVersionService,
        entityPropertyService,
        codeSystemService,
        conceptService
    )

    when:
    def expanded = service.expand(valueSetVersion(), "lt")

    then:
    expanded.first().additionalDesignations.find { it.language == "lt" }?.name == "ml"
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
