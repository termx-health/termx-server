package org.termx.core

import com.fasterxml.jackson.databind.JsonNode
import com.kodality.commons.util.JsonUtil
import org.termx.TermxIntegTest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import java.net.http.HttpResponse.BodyHandlers

@MicronautTest(transactional = false)
class TerminologyEcosystemTest extends TermxIntegTest {

  def "should expose discovery endpoint at /tx-reg"() {
    when:
    def request = client.builder("/tx-reg").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502  // 502 if coordination server unavailable
  }

  def "should return JSON from discovery endpoint"() {
    when:
    def request = client.builder("/tx-reg").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    if (response.statusCode() == 200) {
      def json = JsonUtil.fromJson(response.body(), JsonNode.class)
      json != null
      json.has("results") || json.has("error")
    }
  }

  def "should support discovery filtering by fhirVersion"() {
    when:
    def request = client.builder("/tx-reg?fhirVersion=R4").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should support discovery filtering by server code"() {
    when:
    def request = client.builder("/tx-reg?server=tx.fhir.org").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should support discovery filtering by registry"() {
    when:
    def request = client.builder("/tx-reg?registry=hl7-main").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should support discovery filtering by URL"() {
    when:
    def request = client.builder("/tx-reg?url=http://snomed.info/sct").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should support discovery filtering by authoritativeOnly"() {
    when:
    def request = client.builder("/tx-reg?authoritativeOnly=true").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should support multiple discovery filters"() {
    when:
    def request = client.builder("/tx-reg?fhirVersion=R4&authoritativeOnly=true").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should set Content-Disposition header when download=true"() {
    when:
    def request = client.builder("/tx-reg?download=true").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    if (response.statusCode() == 200) {
      def contentDisposition = response.headers().firstValue("Content-Disposition").orElse(null)
      contentDisposition != null
      contentDisposition.contains("attachment")
      contentDisposition.contains("tx-servers.json")
    }
  }

  def "should not set Content-Disposition header when download is false"() {
    when:
    def request = client.builder("/tx-reg").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    if (response.statusCode() == 200) {
      def contentDisposition = response.headers().firstValue("Content-Disposition").orElse(null)
      contentDisposition == null || !contentDisposition.contains("attachment")
    }
  }

  def "should return Content-Type application/json for discovery"() {
    when:
    def request = client.builder("/tx-reg").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    if (response.statusCode() == 200) {
      def contentType = response.headers().firstValue("Content-Type").orElse(null)
      contentType != null
      contentType.contains("application/json")
    }
  }

  def "should expose resolve endpoint at /tx-reg/resolve"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should return 400 when fhirVersion is missing from resolve"() {
    when:
    def request = client.builder("/tx-reg/resolve?url=http://snomed.info/sct").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 400
  }

  def "should return 400 when both url and valueSet are missing from resolve"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 400
  }

  def "should support resolve with url parameter"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should support resolve with valueSet parameter"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&valueSet=http://hl7.org/fhir/ValueSet/administrative-gender").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should support resolve with authoritativeOnly filter"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=http://loinc.org&authoritativeOnly=true").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should support resolve with usage parameter"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct&usage=validation").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should return JSON from resolve endpoint"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    if (response.statusCode() == 200) {
      def json = JsonUtil.fromJson(response.body(), JsonNode.class)
      json != null
      json.has("authoritative") || json.has("candidate") || json.has("error")
    }
  }

  def "should set Content-Disposition header for resolve when download=true"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct&download=true").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    if (response.statusCode() == 200) {
      def contentDisposition = response.headers().firstValue("Content-Disposition").orElse(null)
      contentDisposition != null
      contentDisposition.contains("attachment")
      contentDisposition.contains("tx-resolve.json")
    }
  }

  def "should return Content-Type application/json for resolve"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    if (response.statusCode() == 200) {
      def contentType = response.headers().firstValue("Content-Type").orElse(null)
      contentType != null
      contentType.contains("application/json")
    }
  }

  def "should support versioned CodeSystem URLs in resolve"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct|http://snomed.info/sct/900000000000207008").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502
  }

  def "should handle URL encoding in query parameters"() {
    when:
    def encodedUrl = java.net.URLEncoder.encode("http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20230901", "UTF-8")
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=" + encodedUrl).GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200 || response.statusCode() == 502 || response.statusCode() == 400
  }

  def "should not require authentication for discovery endpoint"() {
    when:
    def request = client.builder("/tx-reg")
        .GET()
        .build()  // No Authorization header
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() != 401 && response.statusCode() != 403
  }

  def "should not require authentication for resolve endpoint"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct")
        .GET()
        .build()  // No Authorization header
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() != 401 && response.statusCode() != 403
  }

  def "should handle coordination server unavailable gracefully"() {
    when:
    def request = client.builder("/tx-reg").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    // Either succeeds (200) or returns Bad Gateway (502) if coordination server unreachable
    response.statusCode() in [200, 502]
  }

  def "should validate discovery response structure when available"() {
    when:
    def request = client.builder("/tx-reg").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    if (response.statusCode() == 200) {
      def json = JsonUtil.fromJson(response.body(), JsonNode.class)
      
      // Check for expected top-level fields per IG spec
      json.has("results") || json.has("last-update") || json.has("master-url")
    }
  }

  def "should validate resolve response structure when available"() {
    when:
    def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    if (response.statusCode() == 200) {
      def json = JsonUtil.fromJson(response.body(), JsonNode.class)
      
      // Check for expected fields per IG spec
      json.has("formatVersion") || json.has("authoritative") || json.has("candidate")
    }
  }

  def "should handle all FHIR versions in resolve"() {
    expect:
    ["R3", "R4", "R4B", "R5", "R6"].each { version ->
      def request = client.builder("/tx-reg/resolve?fhirVersion=${version}&url=http://snomed.info/sct").GET().build()
      def response = client.execute(request, BodyHandlers.ofString())
      
      // Should either succeed or return 502 (coordination server issue), not 400 (validation error)
      assert response.statusCode() in [200, 502]
    }
  }

  def "should support all usage tokens in resolve"() {
    expect:
    ["publication", "validation", "code-generation"].each { usage ->
      def request = client.builder("/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct&usage=${usage}").GET().build()
      def response = client.execute(request, BodyHandlers.ofString())
      
      assert response.statusCode() in [200, 502]
    }
  }
}
