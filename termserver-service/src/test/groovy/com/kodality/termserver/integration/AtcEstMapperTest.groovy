package com.kodality.termserver.integration

import com.kodality.commons.model.LocalizedName

import com.kodality.termserver.integration.atcest.utils.AtcEst
import com.kodality.termserver.integration.atcest.utils.AtcEstMapper
import spock.lang.Specification

import java.time.LocalDate

class AtcEstMapperTest extends Specification {

  def atcs = [
      new AtcEst(code: 'A', name: 'SEEDEKULGLA JA AINEVAHETUS'),
      new AtcEst(code: 'A01', name: 'STOMATOLOOGILISED PREPARAADID'),
      new AtcEst(code: 'A01A', name: 'STOMATOLOOGILISED PREPARAADID-2'),
      new AtcEst(code: 'A01AA', name: 'Ained kaariese profülaktikaks'),
      new AtcEst(code: 'A01AA01', name: 'naatriumfluoriid')
  ]

  def configuration = new ImportConfiguration(
      uri: 'https://ravimiregister.ee',
      source: 'Ravimiregister',
      version: '2022',
      validFrom: LocalDate.of(2022, 1, 1),
      codeSystem: 'atc-est',
      codeSystemName: new LocalizedName(Map.of("et", "ATC est")),
      codeSystemDescription: 'Eesti ATC (Anatomical Therapeutic Chemical Classification System)'
  )

  def "should map ATC concepts"() {
    when:
    def concepts = AtcEstMapper.mapCodeSystem(configuration, atcs).concepts

    then:
    concepts.size() == 5
    concepts.codeSystem == ['atc-est', 'atc-est', 'atc-est', 'atc-est', 'atc-est']
    concepts.code.sort() == ['A', 'A01', 'A01A', 'A01AA', 'A01AA01']
    concepts.versions.size() == 5
    concepts.versions[0][0].status == 'draft'
    concepts.versions[0][0].code == 'A'
    concepts.versions[0][0].designations.size() == 1
    concepts.versions[0][0].designations[0].status == 'active'
    concepts.versions[0][0].designations[0].name == 'SEEDEKULGLA JA AINEVAHETUS'
    concepts.versions[0][0].designations[0].language == 'et'
    concepts.versions[0][0].associations.size() == 0

    concepts.versions[1][0].code == 'A01'
    concepts.versions[1][0].associations.size() == 1
    concepts.versions[1][0].associations[0].status == 'active'
    concepts.versions[1][0].associations[0].targetCode == 'A'

    concepts.versions[2][0].code == 'A01A'
    concepts.versions[2][0].associations.size() == 1
    concepts.versions[2][0].associations[0].status == 'active'
    concepts.versions[2][0].associations[0].targetCode == 'A01'
  }
}
