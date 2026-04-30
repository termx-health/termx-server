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
}
