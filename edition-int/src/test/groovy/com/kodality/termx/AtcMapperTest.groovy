package com.kodality.termx

import com.kodality.commons.model.LocalizedName
import com.kodality.termx.editionint.atc.utils.AtcMapper
import com.kodality.termx.ts.Language
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration
import spock.lang.Specification

import java.time.LocalDate

class AtcMapperTest extends Specification {
  def atcs = new HashMap(Map.of(
      'A', 'ALIMENTARY TRACT AND METABOLISM',
      'A01', 'STOMATOLOGICAL PREPARATIONS',
      'A01A', 'STOMATOLOGICAL PREPARATIONS'
  ))

  def configuration = new CodeSystemImportConfiguration(
      uri: 'http://www.whocc.no/atc',
      publisher: 'WHO',
      version: '2022',
      validFrom: LocalDate.of(2022, 1, 1),
      codeSystem: 'atc-int',
      codeSystemName: new LocalizedName(Map.of(Language.en, "ATC int")),
      codeSystemDescription: [en: 'Anatomical Therapeutic Chemical Classification System']
  )

  def "should map ATC concepts"() {
    when:
    def concepts = AtcMapper.toRequest(configuration, atcs).concepts

    then:
    concepts.size() == 3
    concepts.code.sort() == ['A', 'A01', 'A01A']
    concepts.designations.size() == 3
  }
}
