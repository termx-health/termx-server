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
 * tx-ecosystem supplement-good (P6b.1): a `useSupplement` $expand must NOT echo the raw `useSupplement`
 * request param, and MUST report the supplement it applied as a derived `used-supplement` parameter with
 * the resolved version (`<url>|0.1.1`). Mirrors `parameters-expand-supplement-good`.
 */
@Slf4j
@MicronautTest(transactional = true)
class SupplementExpandEchoIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject ValueSetFhirImportService vsImportService
  @Inject ValueSetExpandOperation vsExpand

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    importResource("extensions/codesystem-extensions.json")
    importResource("extensions/codesystem-supplement.json")
    importResource("extensions/valueset-extensions-all-ns.json")
  }

  def "expand-supplement-good echoes used-supplement|version and drops raw useSupplement"() {
    given:
    def request = toFhir("parameters/parameters-expand-supplement-good-request.json", Parameters.class)

    when:
    ValueSet result = vsExpand.run(request)
    def params = result.expansion?.parameter?.collect { "${it.name}=${it.valueUri ?: it.valueBoolean ?: it.valueString}" }

    then:
    log.info("SUPP-ECHO params={}", params)
    // raw request param is not echoed
    result.expansion.parameter.every { it.name != "useSupplement" }
    // derived used-supplement carries the resolved version
    result.expansion.parameter.any {
      it.name == "used-supplement" && it.valueUri == "http://hl7.org/fhir/test/CodeSystem/supplement|0.1.1"
    }
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
