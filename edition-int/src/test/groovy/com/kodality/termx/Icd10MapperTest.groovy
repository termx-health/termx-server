package com.kodality.termx

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.kodality.commons.model.LocalizedName
import com.kodality.termx.editionint.icd10.utils.Icd10
import com.kodality.termx.editionint.icd10.utils.Icd10Mapper
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest
import com.kodality.termx.core.utils.MatcherUtil
import com.kodality.termx.core.utils.ResourceUtil
import com.kodality.termx.core.utils.XmlMapperUtil
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDate
import java.util.stream.Collectors

class Icd10MapperTest extends Specification {

  @Shared
  private XmlMapper mapper
  @Shared
  private String xml
  @Shared
  private List<CodeSystemImportRequest.CodeSystemImportRequestConcept> concepts
  private Icd10 icd10

  void setupSpec() {
    mapper = XmlMapperUtil.getMapper();
    xml = ResourceUtil.readAsString("icd10.xml")
  }

  void setup() {
    icd10 = mapper.readValue(xml, Icd10.class)
    concepts = new ArrayList<>()
  }

  def configuration = new CodeSystemImportConfiguration(
      uri: 'https://ftp.cdc.gov/pub/Health_Statistics/NCHS/Publications/ICD10CM/2022/',
      publisher: 'World health organization',
      version: '10',
      validFrom: LocalDate.of(2022, 1, 1),
      codeSystem: 'icd-10-int',
      codeSystemName: new LocalizedName(Map.of("et", "ICD-10")),
      codeSystemDescription: [en: 'International Statistical Classification of Diseases and Related Health Problems 10th Revision']
  )

  def "Should create concept components"() {
    when:
    concepts = Icd10Mapper.toRequest(configuration, icd10).concepts
    then:
    concepts.size() == MatcherUtil.findAllMatches(xml, "<Class").size()
    concepts.size() == concepts.stream().flatMap(c -> c.associations.stream()).collect(Collectors.toList()).size()
  }

  def "Should create designations"() {
    when:
    concepts = Icd10Mapper.toRequest(configuration, icd10).concepts
    then:
    concepts.stream().flatMap(c -> c.designations.stream()).collect(Collectors.toList()).size() == MatcherUtil.findAllMatches(xml, "<Rubric").size()
  }
}
