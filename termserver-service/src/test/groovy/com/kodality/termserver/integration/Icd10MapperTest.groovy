package com.kodality.termserver.integration

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.kodality.commons.model.LocalizedName
import com.kodality.termserver.codesystem.Concept
import com.kodality.termserver.common.ImportConfiguration
import com.kodality.termserver.common.utils.MatcherUtil
import com.kodality.termserver.common.utils.TemplateUtil
import com.kodality.termserver.common.utils.XmlMapperUtil
import com.kodality.termserver.integration.icd10.utils.Icd10
import com.kodality.termserver.integration.icd10.utils.Icd10Mapper
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
  private List<Concept> concepts
  private Icd10 icd10

  void setupSpec() {
    mapper = XmlMapperUtil.getMapper();
    xml = TemplateUtil.getTemplate("icd10.xml")
  }

  void setup() {
    icd10 = mapper.readValue(xml, Icd10.class)
    concepts = new ArrayList<>()
  }

  def configuration = new ImportConfiguration(
      uri: 'https://ftp.cdc.gov/pub/Health_Statistics/NCHS/Publications/ICD10CM/2022/',
      source: 'World health organization',
      version: '10',
      validFrom: LocalDate.of(2022, 1, 1),
      codeSystem: 'icd-10-int',
      codeSystemName: new LocalizedName(Map.of("et", "ICD-10")),
      codeSystemDescription: 'International Statistical Classification of Diseases and Related Health Problems 10th Revision'
  )

  def "Should create concept components"() {
    when:
    concepts = Icd10Mapper.mapCodeSystem(configuration, icd10).concepts
    then:
    concepts.size() == MatcherUtil.findAllMatches(xml, "<Class").size()
    concepts.size() == concepts.stream().flatMap(c -> c.getVersions().stream().flatMap(v -> v.getAssociations().stream())).collect(Collectors.toList()).size()
  }

  def "Should create designations"() {
    when:
    concepts = Icd10Mapper.mapCodeSystem(configuration, icd10).concepts
    then:
    //- 1 because of root classifier
    concepts.stream().flatMap(c -> c.getVersions().stream().flatMap(v -> v.getDesignations().stream())).collect(Collectors.toList()).size()
        == MatcherUtil.findAllMatches(xml, "<Rubric").size()
  }
}
