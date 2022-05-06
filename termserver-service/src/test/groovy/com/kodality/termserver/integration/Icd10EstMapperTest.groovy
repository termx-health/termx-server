package com.kodality.termserver.integration

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.kodality.commons.model.LocalizedName
import com.kodality.termserver.codesystem.Concept
import com.kodality.termserver.common.ImportConfiguration
import com.kodality.termserver.common.utils.TemplateUtil
import com.kodality.termserver.common.utils.XmlMapperUtil
import com.kodality.termserver.integration.icd10est.utils.Extractor
import com.kodality.termserver.integration.icd10est.utils.Icd10Est
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDate
import java.util.stream.Collectors

class Icd10EstMapperTest extends Specification {
  @Shared
  private XmlMapper mapper
  @Shared
  private String xml
  @Shared
  private List<Concept> concepts
  private Icd10Est icd10Est

  void setupSpec() {
    mapper = XmlMapperUtil.getMapper()
    xml = TemplateUtil.getTemplate("icd10-est.xml")
  }

  void setup() {
    icd10Est = mapper.readValue(xml, Icd10Est.class)
    concepts = new ArrayList<>()
  }

  def configuration = new ImportConfiguration(
      uri: 'https://pub.e-tervis.ee/classifications/RHK-10/8',
      source: 'Ministry of Social Affairs of Estonia',
      version: '8',
      validFrom: LocalDate.of(2022, 1, 1),
      codeSystem: 'icd-10-est',
      codeSystemName: new LocalizedName(Map.of("et", "RHK-10")),
      codeSystemDescription: 'RHK-10 on rahvusvaheline haiguste ja nendega seotud terviseprobleemide statistiline klassifikatsioon, mille sisu haldaja on Sotsiaalministeerium. Täiendus- ja muudatusettepanekud edastada info@sm.ee.'
  )

  def "Should create concept components"() {
    when:
    concepts = Extractor.parseNodeChild(icd10Est.getChapter(), Extractor.rootNode(), configuration, [])
    then:
    concepts.size() == MatcherUtil.findAllMatches(xml, "<chapter>|<section>|<item>|<subsection>|<sub>").size()
    concepts.size() == concepts.stream().flatMap(c -> c.getVersions().stream()).collect(Collectors.toList()).size()
    concepts.size() == concepts.stream().flatMap(c -> c.getVersions().stream().flatMap(v -> v.getAssociations().stream())).collect(Collectors.toList()).size()
  }

  def "Should create designations"() {
    when:
    concepts = Extractor.parseNodeChild(icd10Est.getChapter(), Extractor.rootNode(), configuration, [])
    then:
    concepts.stream().flatMap(c -> c.getVersions().stream().flatMap(v -> v.getDesignations().stream())).collect(Collectors.toList()).size()
        == MatcherUtil.findAllMatches(xml, "<name-est>|<name-eng>|<name-lat>").size()
  }
}
