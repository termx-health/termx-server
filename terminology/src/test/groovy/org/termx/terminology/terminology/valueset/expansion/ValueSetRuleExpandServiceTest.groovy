package org.termx.terminology.terminology.valueset.expansion

import com.kodality.zmei.fhir.resource.terminology.ValueSet as FhirValueSet
import org.termx.core.ts.ValueSetExternalExpandProvider
import org.termx.terminology.fhir.valueset.ValueSetFhirMapper
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemVersion
import org.termx.ts.codesystem.CodeSystemVersionReference
import org.termx.ts.valueset.ValueSet
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionConcept
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import spock.lang.Specification

class ValueSetRuleExpandServiceTest extends Specification {
  def valueSetService = Mock(ValueSetService)
  def valueSetVersionService = Mock(ValueSetVersionService)
  def codeSystemService = Mock(CodeSystemService)
  def codeSystemVersionService = Mock(CodeSystemVersionService)
  def repository = Mock(ValueSetVersionConceptRepository)
  def conceptService = Mock(ValueSetVersionConceptService)
  def fhirMapper = Mock(ValueSetFhirMapper)

  def service = new ValueSetRuleExpandService(
      [],
      valueSetService,
      valueSetVersionService,
      codeSystemService,
      codeSystemVersionService,
      repository,
      conceptService,
      fhirMapper
  )

  def "expandRule resolves URIs and filters inactive concepts when inactiveConcepts is false"() {
    given:
    def valueSet = new ValueSet().setId("vs").setUri("http://example.org/vs")
    def version = new ValueSetVersion()
        .setId(10L)
        .setValueSet("vs")
        .setVersion("1.0.0")
        .setStatus("draft")
        .setPreferredLanguage("en")
    def codeSystemVersion = new CodeSystemVersion()
        .setId(12L)
        .setVersion("2024")
        .setUri("http://example.org/cs|2024")
        .setBaseCodeSystemVersion(new CodeSystemVersionReference().setId(13L).setVersion("base").setUri("http://example.org/base|base"))
    def requestRule = new ValueSetVersionRule()
        .setType("include")
        .setCodeSystem("contact-point-use")
        .setCodeSystemVersion(new CodeSystemVersionReference().setId(12L))
    def rawConcepts = [new ValueSetVersionConcept(), new ValueSetVersionConcept()]
    def decorated = [
        new ValueSetVersionConcept().setActive(true),
        new ValueSetVersionConcept().setActive(false)
    ]

    when:
    def result = service.expandRule("vs", "1.0.0", requestRule, false)

    then:
    1 * valueSetService.load("vs") >> valueSet
    1 * valueSetVersionService.load("vs", "1.0.0") >> Optional.of(version)
    1 * codeSystemService.load("contact-point-use") >> Optional.of(new CodeSystem().setId("contact-point-use").setUri("http://example.org/cs").setBaseCodeSystemUri("http://example.org/base"))
    1 * codeSystemVersionService.load(12L) >> codeSystemVersion
    1 * fhirMapper.toFhir(valueSet, {
      it.ruleSet.rules.size() == 1 &&
          it.ruleSet.rules.first().codeSystemUri == "http://example.org/cs" &&
          it.ruleSet.rules.first().codeSystemBaseUri == "http://example.org/base" &&
          it.ruleSet.rules.first().codeSystemVersion.uri == "http://example.org/cs|2024"
    }, []) >> new FhirValueSet()
    1 * repository.expandFromJson(_) >> rawConcepts
    1 * conceptService.decorate(rawConcepts, _, "en") >> decorated
    result.size() == 1
    result.first().active
  }

  def "expandRule includes external provider concepts"() {
    given:
    def externalConcept = new ValueSetVersionConcept().setActive(true)
    def service = new ValueSetRuleExpandService(
        [provider("ucum", externalConcept)],
        valueSetService,
        valueSetVersionService,
        codeSystemService,
        codeSystemVersionService,
        repository,
        conceptService,
        fhirMapper
    )
    def valueSet = new ValueSet().setId("vs").setUri("http://example.org/vs")
    def version = new ValueSetVersion()
        .setId(10L)
        .setValueSet("vs")
        .setVersion("1.0.0")
        .setStatus("draft")
        .setPreferredLanguage("en")
    def requestRule = new ValueSetVersionRule().setType("include").setCodeSystem("ucum")

    when:
    def result = service.expandRule("vs", "1.0.0", requestRule, false)

    then:
    1 * valueSetService.load("vs") >> valueSet
    1 * valueSetVersionService.load("vs", "1.0.0") >> Optional.of(version)
    1 * codeSystemService.load("ucum") >> Optional.of(new CodeSystem().setId("ucum").setUri("http://unitsofmeasure.org"))
    0 * codeSystemVersionService._
    1 * fhirMapper.toFhir(valueSet, _, []) >> new FhirValueSet()
    1 * repository.expandFromJson(_) >> []
    1 * conceptService.decorate([], _, "en") >> []
    result == [externalConcept]
  }

  private static ValueSetExternalExpandProvider provider(String codeSystemId, ValueSetVersionConcept concept) {
    return new ValueSetExternalExpandProvider() {
      @Override
      List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule, ValueSetVersion version, String preferredLanguage) {
        return [concept]
      }

      @Override
      String getCodeSystemId() {
        return codeSystemId
      }
    }
  }
}
