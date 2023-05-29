package com.kodality.termserver.fileimporter.codesystem

import com.kodality.commons.model.QueryResult
import com.kodality.termserver.fhir.codesystem.CodeSystemFhirImportService
import com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest
import com.kodality.termserver.terminology.codesystem.CodeSystemImportService
import com.kodality.termserver.terminology.codesystem.CodeSystemService
import com.kodality.termserver.terminology.codesystem.CodeSystemValidationService
import com.kodality.termserver.terminology.codesystem.CodeSystemVersionService
import com.kodality.termserver.terminology.codesystem.compare.CodeSystemCompareService
import com.kodality.termserver.terminology.codesystem.concept.ConceptService
import com.kodality.termserver.terminology.valueset.ValueSetService
import com.kodality.termserver.terminology.valueset.ValueSetVersionService
import com.kodality.termserver.terminology.valueset.concept.ValueSetVersionConceptService
import com.kodality.termserver.terminology.valueset.ruleset.ValueSetVersionRuleService
import com.kodality.termserver.ts.PublicationStatus
import com.kodality.termserver.ts.codesystem.*
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept
import spock.lang.Specification

import static com.kodality.termserver.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingProperty
import static com.kodality.termserver.ts.codesystem.EntityProperty.EntityPropertyRule
import static com.kodality.termserver.ts.codesystem.EntityPropertyType.coding
import static com.kodality.termserver.ts.codesystem.EntityPropertyType.string
import static com.kodality.termserver.ts.codesystem.EntityPropertyValue.EntityPropertyValueCodingValue
import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals

class CodeSystemImportServiceTest extends Specification {
  CodeSystemCompareService codeSystemCompareService = Mock(CodeSystemCompareService)
  CodeSystemFhirImportService codeSystemFhirImportService = Mock(CodeSystemFhirImportService)
  CodeSystemImportService codeSystemImportService = Mock(CodeSystemImportService)
  CodeSystemService codeSystemService = Mock(CodeSystemService)
  CodeSystemValidationService codeSystemValidationService = Mock(CodeSystemValidationService)
  CodeSystemVersionService codeSystemVersionService = Mock(CodeSystemVersionService)
  ConceptService conceptService = Mock(ConceptService)
  ValueSetService valueSetService = Mock(ValueSetService)
  ValueSetVersionConceptService valueSetVersionConceptService = Mock(ValueSetVersionConceptService)
  ValueSetVersionRuleService valueSetVersionRuleService = Mock(ValueSetVersionRuleService)
  ValueSetVersionService valueSetVersionService = Mock(ValueSetVersionService)

  CodeSystemFileImportService service = new CodeSystemFileImportService(
      codeSystemCompareService,
      codeSystemFhirImportService,
      codeSystemImportService,
      codeSystemService,
      codeSystemValidationService,
      codeSystemVersionService,
      conceptService,
      valueSetService,
      valueSetVersionConceptService,
      valueSetVersionRuleService,
      valueSetVersionService,
  )

  EntityProperty entityProperty(String code, String name) {
    // @see FileProcessingMapper.toProperties
    new EntityProperty(type: code, name: name, status: PublicationStatus.active)
  }


  def 'should set default values to CS concept version property values'() {
    given:
    def ep = new EntityProperty(
        id: 42,
        type: string,
        name: 'description',
        status: PublicationStatus.active,
        required: true,
    )

    def cs = new CodeSystem(id: 'overlord', properties: [entityProperty(ep.type, ep.name)], concepts: [
        new Concept(code: 'test', versions: [
            new CodeSystemEntityVersion(codeSystem: 'overlord', code: 'test', designations: [], propertyValues: [
                new EntityPropertyValue(entityProperty: ep.name, value: [code: 'code-1']),
            ])
        ])
    ])

    when:
    service.prepareEntityProperties(cs, [ep])

    then:
    conceptService.query(_) >> { args -> QueryResult.empty() }

    def property = cs.getProperties()[0]
    property.type == ep.type
    property.name == ep.name
    property.rule == ep.rule
    property.required == ep.required
  }

  def 'should populate EPV with codes from VS expand concepts'() {
    def ep = new EntityProperty(
        id: 42,
        type: coding,
        name: 'ref',
        rule: new EntityPropertyRule(valueSet: 'vs-1'),
        status: PublicationStatus.active,
        required: true,
    )

    def req = new FileProcessingRequest(
        properties: [new FileProcessingProperty(columnName: 'xxx', propertyName: ep.name, propertyType: ep.type)]
    )

    def cs = new CodeSystem(id: 'overlord', properties: [entityProperty(ep.type, ep.name)], concepts: [
        new Concept(code: 'test', versions: [
            new CodeSystemEntityVersion(codeSystem: 'overlord', code: 'test', designations: [], propertyValues: [
                new EntityPropertyValue(entityProperty: ep.name, value: [code: 'code-1']),
                new EntityPropertyValue(entityProperty: ep.name, value: [code: 'Concept 2']),
                new EntityPropertyValue(entityProperty: ep.name, value: [code: 'Concept 3']),
            ])
        ])
    ])

    when:
    def issues = service.validate(req, cs, new CodeSystem(properties: [ep]))

    then:
    codeSystemValidationService.validateConcepts(_, _) >> []
    valueSetVersionConceptService.expand(ep.rule.valueSet, _, _) >> [
        new ValueSetVersionConcept(
            concept: new Concept(code: 'code-1', codeSystem: 'cs-1', versions: [version('#')])),
        new ValueSetVersionConcept(
            concept: new Concept(code: 'code-2', codeSystem: 'cs-1', versions: [version('Concept 2')])),
        new ValueSetVersionConcept(
            concept: new Concept(code: 'code-3', codeSystem: 'cs-2', versions: [version('#')]),
            additionalDesignations: [new Designation(designationType: 'display', name: 'Concept 3')]),
    ]

    issues.size() ==0
    def values = cs.concepts[0].versions[0].propertyValues
    reflectionEquals(values[0].value, new EntityPropertyValueCodingValue('code': 'code-1', 'codeSystem': 'cs-1'))
    reflectionEquals(values[1].value, new EntityPropertyValueCodingValue('code': 'code-2', 'codeSystem': 'cs-1'))
    reflectionEquals(values[2].value, new EntityPropertyValueCodingValue('code': 'code-3', 'codeSystem': 'cs-2'))

  }

  def 'should validate CS concepts'() {
    given:
    def ep = new EntityProperty(
        id: 42,
        type: coding,
        name: 'external',
        rule: new EntityPropertyRule(codeSystems: ['cs-1']),
        status: PublicationStatus.active,
        required: true,
    )

    def req = new FileProcessingRequest(
        properties: [new FileProcessingProperty(columnName: 'xxx', propertyName: ep.name, propertyType: ep.type)]
    )

    def cs = new CodeSystem(id: 'overlord', properties: [entityProperty(ep.type, ep.name)], concepts: [
        new Concept(code: 'test', versions: [
            new CodeSystemEntityVersion(codeSystem: 'overlord', code: 'test', designations: [], propertyValues: [
                new EntityPropertyValue(entityProperty: ep.name, value: [code: 'code-1']),     // (1) OK, code-1 exists in cs-1
                new EntityPropertyValue(entityProperty: ep.name, value: [code: 'Concept 2']),  // (2) OK, code-2 concept gets found by designation in the cs-1
                new EntityPropertyValue(entityProperty: ep.name, value: [code: 'code-3']),     // (3) FAIL, code-3 does not exist in cs-1
                new EntityPropertyValue(entityProperty: ep.name, value: [code: 'Concept 4']),  // (4) FAIL, multiple candidates found by designation
            ])
        ])
    ])

    when:
    def issues = service.validate(req, cs, new CodeSystem(properties: [ep]))

    then:
    codeSystemValidationService.validateConcepts(_, _) >> []
    conceptService.query(_) >> { args ->
      def params = args[0] as ConceptQueryParams
      def body = () -> {
        if (params.code)
          return [
              new Concept(code: 'code-1', codeSystem: 'cs-1', versions: []),
              new Concept(code: 'code-5', codeSystem: 'cs-1', versions: []),
          ]
        if (params.designationCiEq)
          return [
              new Concept(code: 'code-2', codeSystem: 'cs-1', versions: [version('Concept 2')]),
              new Concept(code: 'code-4.1', codeSystem: 'cs-1', versions: [version('Concept 4')]),
              new Concept(code: 'code-4.2', codeSystem: 'cs-1', versions: [version('Concept 4')]),
          ]
        return []
      }
      return new QueryResult(body())
    }

    def expectedIssues = [
        'Several concepts match the "Concept 4" value',
        'Unknown reference "code-3" to "cs-1"',
    ]

    expectedIssues.containsAll(issues.collect { it.formattedMessage() })
    expectedIssues.size() == issues.size()


    def values = cs.concepts[0].versions[0].propertyValues
    reflectionEquals(values[0].value, new EntityPropertyValueCodingValue('code': 'code-1', 'codeSystem': 'cs-1')) // (1)
    reflectionEquals(values[1].value, new EntityPropertyValueCodingValue('code': 'code-2', 'codeSystem': 'cs-1')) // (2)
    values[2].value == ['code': 'code-3'] // (3)
    values[3].value == ['code': 'Concept 4'] // (4)
  }

  def version = (designationName) -> {
    return new CodeSystemEntityVersion(designations: [new Designation(designationType: 'display', name: designationName)])
  }
}
