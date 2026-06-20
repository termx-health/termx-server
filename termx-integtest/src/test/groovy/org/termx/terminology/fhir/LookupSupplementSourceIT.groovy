package org.termx.terminology.fhir

import com.kodality.kefhir.structure.api.ResourceContent
import com.kodality.zmei.fhir.FhirMapper
import com.kodality.zmei.fhir.resource.Resource
import com.kodality.zmei.fhir.resource.other.Parameters
import groovy.util.logging.Slf4j
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.codesystem.operations.CodeSystemLookupOperation

/**
 * tx-ecosystem lookup-supplement-good (P6b.2): a $lookup with useSupplement layers the supplement's
 * designation (nl "ectenoot") rendered with a `source` part = <supplement>|<version>, and reports the
 * applied supplement as a `used-supplement` parameter. Mirrors `parameters-lookup-supplement-good`.
 */
@Slf4j
@MicronautTest(transactional = true)
class LookupSupplementSourceIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject CodeSystemLookupOperation lookup

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    importResource("extensions/codesystem-extensions.json")
    importResource("extensions/codesystem-supplement.json")
  }

  def "lookup useSupplement: nl designation carries source part + used-supplement param"() {
    given:
    def reqJson = readJson("parameters/parameters-lookup-supplement-good-request.json")

    when:
    def respContent = lookup.run(new ResourceContent(reqJson, "json"))
    Parameters resp = FhirMapper.fromJson(respContent.getValue(), Parameters.class)
    def designations = resp.getParameter().findAll { it.name == "designation" }
    def nl = designations.find { dg -> dg.part.any { it.name == "language" && it.valueString == "nl" } }
    def nlSource = nl?.part?.find { it.name == "source" }?.valueCanonical
    def nlValue = nl?.part?.find { it.name == "value" }?.valueString
    def nlHasUse = nl?.part?.any { it.name == "use" }
    def usedSupplement = resp.getParameter().find { it.name == "used-supplement" }?.valueCanonical

    then:
    log.info("LOOKUP-SUPP nlValue={} nlSource={} nlHasUse={} usedSupplement={}", nlValue, nlSource, nlHasUse, usedSupplement)
    nlValue == "ectenoot"
    nlSource == "http://hl7.org/fhir/test/CodeSystem/supplement|0.1.1"
    !nlHasUse
    usedSupplement == "http://hl7.org/fhir/test/CodeSystem/supplement|0.1.1"
  }

  def importResource(String path) {
    def json = readJson(path)
    def res = FhirMapper.fromJson(json, Resource.class)
    if (res.resourceType == "CodeSystem") {
      csImportService.importCodeSystem(json, res.id)
    }
  }

  def readJson(String path) {
    def json = new String(getClass().getClassLoader().getResourceAsStream("fhir/" + path).readAllBytes())
    return json.replace("﻿", "").replace("\$" + "instant" + "\$", "")
  }
}
