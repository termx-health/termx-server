package org.termx.terminology.fhir

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.valueset.ValueSetFhirImportService
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.valueset.ValueSetService

import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers

/**
 * Issue #34 — FHIR batch operations. A {@code POST /fhir} with a {@code Bundle} of
 * {@code type=batch} must run each entry (here a ValueSet {@code $expand} via GET and a
 * {@code $validate-code} via POST) and return a {@code batch-response} Bundle holding each
 * operation's result. The batch plumbing lives in kefhir's {@code BundleService}; this test pins
 * that it actually dispatches TermX terminology operations end-to-end over HTTP.
 */
// transactional = false: the batch runs on a separate HTTP request thread/connection, so the
// fixtures must be committed (not held in the test's rolled-back transaction) to be visible.
@MicronautTest(transactional = false)
class BatchBundleIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject ValueSetFhirImportService vsImportService
  @Inject CodeSystemService csService
  @Inject ValueSetService vsService

  static final ObjectMapper MAPPER = new ObjectMapper()
  static final String VS_URL = "http://hl7.org/fhir/test/ValueSet/simple-enumerated"
  static final String VS_ALL_URL = "http://hl7.org/fhir/test/ValueSet/simple-all"
  static final String CS_URL = "http://hl7.org/fhir/test/CodeSystem/simple"

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    csImportService.importCodeSystem(fixture("fhir/simple/codesystem-simple.json"), "simple")
    vsImportService.importValueSet(fixture("fhir/simple/valueset-enumerated.json"), "simple-enumerated")
    vsImportService.importValueSet(fixture("fhir/simple/valueset-all.json"), "simple-all")
  }

  void cleanup() {
    vsService.cancel("simple-enumerated")
    vsService.cancel("simple-all")
    csService.cancel("simple")
    SessionStore.clearLocal()
  }

  def "POST /fhir batch Bundle runs \$expand and \$validate-code and returns a batch-response"() {
    given: "a batch bundle bundling a GET \$expand and a POST \$validate-code"
    def bundle = """{
      "resourceType": "Bundle",
      "type": "batch",
      "entry": [
        {
          "request": { "method": "GET", "url": "ValueSet/\$expand?url=${VS_URL}&excludeNested=true" }
        },
        {
          "resource": {
            "resourceType": "Parameters",
            "parameter": [
              { "name": "url", "valueUri": "${VS_URL}" },
              { "name": "system", "valueUri": "${CS_URL}" },
              { "name": "code", "valueCode": "code1" }
            ]
          },
          "request": { "method": "POST", "url": "ValueSet/\$validate-code" }
        }
      ]
    }"""

    when:
    def request = client.builder("/fhir")
        .header("Content-Type", "application/fhir+json")
        .header("Accept", "application/fhir+json")
        .header("Authorization", 'Bearer yupi{"username":"test","privileges":["*.*.*"]}')
        .POST(BodyPublishers.ofString(bundle))
        .build()
    def response = client.execute(request, BodyHandlers.ofString())
    def body = MAPPER.readTree(response.body())

    then: "the response is a batch-response with one entry per request"
    response.statusCode() == 200
    body.get("resourceType").asText() == "Bundle"
    body.get("type").asText() == "batch-response"
    body.get("entry").size() == 2

    and: "the \$expand entry returns an expanded ValueSet containing code1"
    def expandResource = resourceOfType(body, "ValueSet")
    expandResource != null
    expansionCodes(expandResource).contains("code1")

    and: "the \$validate-code entry returns result=true for code1"
    def validateResource = resourceOfType(body, "Parameters")
    validateResource != null
    parameterBoolean(validateResource, "result") == true
  }

  def "a batch entry failing with a non-FhirException yields a per-entry OperationOutcome, not a whole-batch 500 (kefhir#7)"() {
    given: "a session scoped to simple-enumerated only, validating a code in BOTH that VS (permitted) and simple-all (forbidden)"
    def bundle = """{
      "resourceType": "Bundle",
      "type": "batch",
      "entry": [
        {
          "resource": { "resourceType": "Parameters", "parameter": [
            { "name": "url", "valueUri": "${VS_URL}" },
            { "name": "system", "valueUri": "${CS_URL}" },
            { "name": "code", "valueCode": "code1" } ] },
          "request": { "method": "POST", "url": "ValueSet/\$validate-code" }
        },
        {
          "resource": { "resourceType": "Parameters", "parameter": [
            { "name": "url", "valueUri": "${VS_ALL_URL}" },
            { "name": "system", "valueUri": "${CS_URL}" },
            { "name": "code", "valueCode": "code1" } ] },
          "request": { "method": "POST", "url": "ValueSet/\$validate-code" }
        }
      ]
    }"""

    when: "the batch is posted with a session that may only read simple-enumerated"
    def request = client.builder("/fhir")
        .header("Content-Type", "application/fhir+json")
        .header("Accept", "application/fhir+json")
        .header("Authorization", 'Bearer yupi{"username":"test","privileges":["simple-enumerated.ValueSet.read"]}')
        .POST(BodyPublishers.ofString(bundle))
        .build()
    def response = client.execute(request, BodyHandlers.ofString())
    def body = MAPPER.readTree(response.body())

    then: "the whole batch still succeeds — before kefhir R5.8 the ForbiddenException 500'd the entire request"
    response.statusCode() == 200
    body.get("type").asText() == "batch-response"
    body.get("entry").size() == 2

    and: "the forbidden entry is its own OperationOutcome with the right status (403, not a blanket 500), and the permitted entry still ran"
    resourceOfType(body, "OperationOutcome") != null
    responseStatusOf(body, "OperationOutcome") == "403"
    resourceOfType(body, "Parameters") != null
  }

  private String fixture(String path) {
    def stream = getClass().getClassLoader().getResourceAsStream(path)
    assert stream != null, "fixture not found: ${path}"
    return new String(stream.readAllBytes()).replace("﻿", "")
  }

  private static JsonNode resourceOfType(JsonNode bundle, String resourceType) {
    for (def entry : bundle.get("entry")) {
      def resource = entry.get("resource")
      if (resource != null && resource.get("resourceType")?.asText() == resourceType) {
        return resource
      }
    }
    return null
  }

  private static String responseStatusOf(JsonNode bundle, String resourceType) {
    for (def entry : bundle.get("entry")) {
      if (entry.get("resource")?.get("resourceType")?.asText() == resourceType) {
        return entry.path("response").path("status").asText(null)
      }
    }
    return null
  }

  private static List<String> expansionCodes(JsonNode valueSet) {
    def contains = valueSet.path("expansion").path("contains")
    return contains.isArray() ? contains.collect { it.get("code").asText() } : []
  }

  private static Boolean parameterBoolean(JsonNode parameters, String name) {
    for (def p : parameters.path("parameter")) {
      if (p.get("name")?.asText() == name) {
        return p.get("valueBoolean")?.asBoolean() ?: false
      }
    }
    return false
  }
}
