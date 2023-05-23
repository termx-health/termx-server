package com.kodality.termserver.terminology.codesystem

import com.kodality.commons.model.QueryResult
import com.kodality.termserver.terminology.codesystem.concept.ConceptService
import com.kodality.termserver.ts.codesystem.*
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
import java.time.LocalDateTime

import static com.kodality.termserver.ts.codesystem.EntityProperty.EntityPropertyRule

class CodeSystemValidationServiceTest extends Specification {
  ConceptService conceptService = Mock(ConceptService)
  CodeSystemValidationService service = new CodeSystemValidationService(conceptService)


  def 'should validate CS version entity properties'() {
    given:
    """
    two concept versions where:
      1. the first version includes a property with a non-existent entity property type (1).
      2. the second version includes a property with an invalid value type (2); and a missing required property (3).
    """


    def code = 'test'
    def concept = new Concept(
        code: code,
        versions: [
            new CodeSystemEntityVersion(
                codeSystem: 'overlord',
                code: code,
                designations: [
                    new Designation(designationType: 'display', name: 'Test concept')
                ],
                propertyValues: [
                    entityPropertyValue('non-existent'), // (1)
                    entityPropertyValue('order', 42i),
                    entityPropertyValue('required-prop', 'exists')
                ]
            ),
            new CodeSystemEntityVersion(
                codeSystem: 'overlord',
                code: code,
                designations: [
                    new Designation(designationType: 'display', name: 'Test concept')
                ],
                propertyValues: [
                    entityPropertyValue('orderable', true),
                    entityPropertyValue('order', 42d), // (2)
                    // (3)
                ]
            )
        ])

    when:
    def issues = service.validateConcepts([concept], [
        entityProperty('display', 'string'),
        entityProperty('orderable', 'boolean'),
        entityProperty('order', 'integer'),
        entityProperty('required-prop', 'string', true),
    ])

    then:
    def expectedIssues = [
        'Unknown entity property: non-existent',
        'Value "42.0" does not match data type "integer"',
        'Required entity property "required-prop" is missing value(s)'
    ]

    issues.collect { it.formattedMessage() }.containsAll(expectedIssues)
    issues.size() == expectedIssues.size()
  }


  def 'should validate CS version Coding entity properties'() {
    given:
    """
    1) an entity property with the type "Coding" and a rule that only allows concepts from the code system "cs-1" to be included; and
    2) a concept with a version that has 2 property values:
       1. valid by all means;
       2. code-2 doesn't exist in cs-1 
       3. invalid by all means
    """

    def ep = entityProperty('external', 'Coding').setRule(new EntityPropertyRule(codeSystems: ['cs-1']))

    def code = 'test'
    def concept = new Concept(
        code: code,
        versions: [
            new CodeSystemEntityVersion(
                codeSystem: 'overlord',
                code: code,
                propertyValues: [
                    entityPropertyValue('external', [codeSystem: 'cs-1', code: 'code-1']),
                    entityPropertyValue('external', [codeSystem: 'cs-1', code: 'code-2']),
                    entityPropertyValue('external', [codeSystem: 'cs-3', code: 'code-3']),
                ]
            )
        ])

    conceptService.query(_) >> { args ->
      def params = args[0] as ConceptQueryParams
      if (params.codeSystem == 'cs-1') {
        return new QueryResult([new Concept(codeSystem: 'cs-1', code: 'code-1')])
      }
      return QueryResult.empty()
    }


    when:
    def issues = service.validateConcepts([concept], [ep])

    then:
    def expectedIssues = [
        'Unknown reference "code-2" to "cs-1"',
        'Unknown reference "code-3" to "cs-3"'
    ]

    issues.collect { it.formattedMessage() }.containsAll(expectedIssues)
    issues.size() == expectedIssues.size()
  }


  private EntityProperty entityProperty(String name, String type, boolean required = false) {
    new EntityProperty(
        name: name,
        type: type,
        required: required
    )
  }

  private EntityPropertyValue entityPropertyValue(String entityProperty, Object value = null) {
    new EntityPropertyValue(
        entityProperty: entityProperty,
        value: value
    )
  }


  @Unroll
  def 'should isValidEntityPropertyType'() {
    expect:
    service.isValidEntityPropertyType(val, type) == result

    where:
    type       | val                           | result
    null       | null                          | false

    'boolean'  | null                          | true
    'boolean'  | true                          | true
    'boolean'  | 'true'                        | false

    'integer'  | null                          | true
    'integer'  | '1'                           | true
    'integer'  | 1i                            | true
    'integer'  | 1l                            | true
    'integer'  | '1.1'                         | false
    'integer'  | 1.1d                          | false
    'integer'  | 1.1f                          | false

    'decimal'  | null                          | true
    'decimal'  | '1.1'                         | true
    'decimal'  | 1.1d                          | true
    'decimal'  | 1.1f                          | true
    'decimal'  | '1'                           | true

    'dateTime' | null                          | true
    'dateTime' | '1998-08-24'                  | true // nb 24-08-1998 gives true, but parsed date is not the same
    'dateTime' | '24.08.98'                    | true
    'dateTime' | '24.08.1998'                  | true
    'dateTime' | '24/1998'                     | true
    'dateTime' | new Date()                    | true
    'dateTime' | LocalDate.now()               | true
    'dateTime' | LocalDateTime.now()           | true
    'dateTime' | 'random'                      | false

    'string'   | 'anything'                    | true
    'string'   | 1                             | true

    'Coding'   | [code: 'C', codeSystem: 'CS'] | true
    'Coding'   | [code: 'C']                   | false

    'code'     | 'stringy'                     | true
    'code'     | 1                             | false
  }
}
