package org.termx.core.fhir

import com.kodality.kefhir.core.model.search.SearchCriterion
import spock.lang.Specification

class BaseFhirMapperSpec extends Specification {

  def 'getSimpleParams filters FHIR result/ignore params and preserves the rest'() {
    given:
    def raw = [
        'url'           : ['https://example.org/ValueSet/foo'],
        '_count'        : ['10'],
        '_page'         : ['2'],
        '_summary'      : ['false'],
        '_elements'     : ['name,status'],
        '_sort'         : ['name'],
        '_include'      : ['ValueSet:reference'],
        '_revinclude'   : ['ConceptMap:source'],
        '_contained'    : ['true'],
        '_containedType': ['contained'],
        '_pretty'       : ['true'],
        '_format'       : ['json'],
        'publisher'     : ['HL7'],
    ]
    def criterion = new SearchCriterion('ValueSet', [], raw)

    when:
    def simple = BaseFhirMapper.getSimpleParams(criterion)

    then:
    simple == [
        'url'      : 'https://example.org/ValueSet/foo',
        '_count'   : '10',
        '_page'    : '2',
        'publisher': 'HL7',
    ]
  }

  def 'getSimpleParams skips empty value lists'() {
    given:
    def raw = ['url': [], '_summary': ['true'], 'publisher': ['HL7']]
    def criterion = new SearchCriterion('ValueSet', [], raw)

    when:
    def simple = BaseFhirMapper.getSimpleParams(criterion)

    then:
    simple == ['publisher': 'HL7']
  }

  def 'parseCompositeId is null-safe'() {
    expect:
    BaseFhirMapper.parseCompositeId(null) == [null, null] as String[]
  }

  def 'fhirIdOrFromUrl prefers the explicit id'() {
    expect:
    BaseFhirMapper.fhirIdOrFromUrl('my-id', 'http://example.org/CodeSystem/ignored') == 'my-id'
    // composite id still resolves to its first segment
    BaseFhirMapper.fhirIdOrFromUrl('my-id' + BaseFhirMapper.SEPARATOR + '1.0.0', 'http://x/y') == 'my-id'
  }

  def 'fhirIdOrFromUrl derives a sanitized id from the url last segment when id is absent'() {
    expect:
    BaseFhirMapper.fhirIdOrFromUrl(id, url) == expected
    where:
    id   | url                                            || expected
    null | 'http://hl7.org/fhir/test/CodeSystem/version'  || 'version'
    ''   | 'http://hl7.org/fhir/test/ValueSet/all-codes'  || 'all-codes'
    null | 'urn:oid:1.2.3.4'                              || 'urn-oid-1.2.3.4'
    null | 'simpleword'                                    || 'simpleword'
    null | null                                            || null
    null | ''                                              || null
  }
}
