package org.termx.terminology.fhir

import com.kodality.zmei.fhir.FhirMapper
import com.kodality.zmei.fhir.resource.Resource
import com.kodality.zmei.fhir.resource.other.Parameters
import com.kodality.zmei.fhir.resource.terminology.ValueSet
import groovy.util.logging.Slf4j
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.valueset.ValueSetFhirImportService
import org.termx.terminology.fhir.valueset.operations.ValueSetExpandOperation

/**
 * tx-ecosystem `simple-expand-enum` (P7): a ValueSet that enumerates codes including ones nested under a
 * parent in the CodeSystem hierarchy (code2a/code2b under code2) must expand to ALL of them, flat, with
 * the notSelectable grouper code2 rendered abstract + inactive.
 */
@Slf4j
@MicronautTest(transactional = true)
class EnumExpandIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject ValueSetFhirImportService vsImportService
  @Inject ValueSetExpandOperation vsExpand

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
  }

  def "simple-expand-enum returns all 5 enumerated codes incl. nested code2a/code2b, code2 abstract+inactive"() {
    given:
    importResource("simple/codesystem-simple.json")
    importResource("simple/valueset-enumerated.json")
    def request = toFhir("simple/simple-expand-enum-request-parameters.json", Parameters.class)

    when:
    ValueSet result = vsExpand.run(request)
    def codes = result.expansion?.contains?.collect { it.code }?.sort(false)
    def code2 = result.expansion?.contains?.find { it.code == "code2" }

    then:
    result.expansion?.total == 5
    codes == ["code1", "code2", "code2a", "code2b", "code3"]
    // code2 is notSelectable + retired in the simple CS → abstract + inactive in the expansion
    code2?.getAbstractField() == true
    code2?.inactive == true
    code2?.display == "Display 2"
  }

  def importResource(String path) {
    def resource = toFhir(path, Resource.class)
    if (resource.resourceType == "CodeSystem") {
      csImportService.importCodeSystem(FhirMapper.toJson(resource), resource.id)
    }
    if (resource.resourceType == "ValueSet") {
      vsImportService.importValueSet(FhirMapper.toJson(resource), resource.id)
    }
  }

  def <T extends Resource> T toFhir(String path, Class<T> clazz) {
    def json = new String(getClass().getClassLoader().getResourceAsStream("fhir/" + path).readAllBytes())
    json = json.replace("﻿", "")
    json = json.replace("\$" + "instant" + "\$", "")
    return FhirMapper.fromJson(json, clazz)
  }
}
