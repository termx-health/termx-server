package com.kodality.termx

import com.kodality.commons.model.LocalizedName
import com.kodality.termx.editionest.atcest.utils.AtcEst
import com.kodality.termx.editionest.atcest.utils.AtcEstMapper
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration
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

  def configuration = new CodeSystemImportConfiguration(
      uri: 'https://ravimiregister.ee',
      publisher: 'Ravimiregister',
      version: '2022',
      validFrom: LocalDate.of(2022, 1, 1),
      codeSystem: 'atc-est',
      codeSystemName: new LocalizedName(Map.of("et", "ATC est")),
      codeSystemDescription: new LocalizedName(["et": 'Eesti ATC (Anatomical Therapeutic Chemical Classification System)'])
  )

  def "should map ATC concepts"() {
    when:
    def concepts = AtcEstMapper.toRequest(configuration, atcs).concepts

    then:
    concepts.size() == 5
    concepts.code.sort() == ['A', 'A01', 'A01A', 'A01AA', 'A01AA01']

    concepts.designations.size() == 5
    concepts.associations.size() == 5
    concepts.properties.size() == 2

    concepts.designations[0][0].name == 'SEEDEKULGLA JA AINEVAHETUS'
  }
}
