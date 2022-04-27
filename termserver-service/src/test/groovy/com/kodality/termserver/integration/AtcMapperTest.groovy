package com.kodality.termserver.integration

import com.kodality.commons.model.LocalizedName
import com.kodality.termserver.integration.atc.utils.AtcMapper
import com.kodality.termserver.integration.common.ImportConfiguration
import spock.lang.Specification

import java.time.LocalDate

class AtcMapperTest extends Specification {
  def atcs = new HashMap(Map.of(
      'A', 'ALIMENTARY TRACT AND METABOLISM',
      'A01', 'STOMATOLOGICAL PREPARATIONS',
      'A01A', 'STOMATOLOGICAL PREPARATIONS'
  ))

  def configuration = new ImportConfiguration(
      uri: 'http://www.whocc.no/atc',
      source: 'WHO',
      version: '2022',
      validFrom: LocalDate.of(2022, 1, 1),
      codeSystem: 'atc-int',
      codeSystemName: new LocalizedName(Map.of("en", "ATC int")),
      codeSystemDescription: 'Anatomical Therapeutic Chemical Classification System'
  )

  def "should map ATC concepts"() {
    when:
    def concepts = AtcMapper.mapConcepts(atcs, configuration, new ArrayList<>())

    then:
    concepts.size() == 3
    concepts.codeSystem == ['atc-int', 'atc-int', 'atc-int']
    concepts.code.sort() == ['A', 'A01', 'A01A']
    concepts.versions.size() == 3

    def A = concepts.versions.find(v -> v.stream().anyMatch(ver -> ver.code == 'A'))
    A[0].code == 'A'
    A[0].status == 'draft'
    A[0].designations.size() == 1
    A[0].designations[0].status == 'active'
    A[0].designations[0].name == 'ALIMENTARY TRACT AND METABOLISM'
    A[0].designations[0].language == 'en'
    A[0].associations.size() == 1
    A[0].associations[0].status == 'active'
    A[0].associations[0].targetCode == 'classification'

    def A01 = concepts.versions.find(v -> v.stream().anyMatch(ver -> ver.code == 'A01'))
    A01[0].code == 'A01'
    A01[0].associations.size() == 1
    A01[0].associations[0].status == 'active'
    A01[0].associations[0].targetCode == 'A'

    def A01A = concepts.versions.find(v -> v.stream().anyMatch(ver -> ver.code == 'A01A'))
    A01A[0].code == 'A01A'
    A01A[0].associations.size() == 1
    A01A[0].associations[0].status == 'active'
    A01A[0].associations[0].targetCode == 'A01'
  }
}
