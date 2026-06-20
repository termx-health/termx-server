package org.termx.terminology.fhir

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
import org.termx.terminology.fhir.valueset.ValueSetFhirImportService
import org.termx.terminology.fhir.valueset.operations.ValueSetExpandOperation

/**
 * tx-ecosystem supplement-bad (P6): a `useSupplement` that names a supplement the server does not host
 * must fail with a not-found OperationOutcome ("Required supplement not found: <url>"), not silently
 * succeed. Mirrors `parameters-expand-supplement-bad`.
 */
@Slf4j
@MicronautTest(transactional = true)
class SupplementMissingIT extends TermxIntegTest {
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

  def "expand-supplement-bad: unresolvable useSupplement is a not-found error, not a silent 200"() {
    given:
    def request = toFhir("parameters/parameters-expand-supplement-bad-request.json", Parameters.class)

    when:
    vsExpand.run(request)

    then:
    def ex = thrown(com.kodality.kefhir.core.exception.FhirException)
    ex.getStatusCode() == 404
    ex.getIssues().any { it.getDetails()?.getText() == "Required supplement not found: http://hl7.org/fhir/test/CodeSystem/supplement-X" }
    // not-found tx-issue-type detail coding, per tx-ecosystem VALUESET_SUPPLEMENT_MISSING
    ex.getIssues().any { it.getDetails()?.getCoding()?.any { c -> c.code == "not-found" } }
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
