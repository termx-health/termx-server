package org.termx.terminology.fhir

import com.fasterxml.jackson.databind.JsonNode
import com.kodality.commons.util.JsonUtil
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest

import java.net.http.HttpResponse.BodyHandlers

@MicronautTest(transactional = false)
class ConformanceStartupOrderingTest extends TermxIntegTest {
  @Inject
  ConformanceSchemaOrderProbe probe

  def "should expose terminology capabilities only after migrated schema is present"() {
    when:
    def request = client.builder("/fhir/metadata").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())

    then:
    response.statusCode() == 200
    probe.terminologyCapabilityVerified

    and:
    JsonNode body = JsonUtil.fromJson(response.body(), JsonNode.class)
    body.get("resourceType").asText() == "CapabilityStatement"
  }

  def "should advertise the configured deployment url, not the one baked into CapabilityStatement.json"() {
    when:
    def request = client.builder("/fhir/metadata").GET().build()
    def response = client.execute(request, BodyHandlers.ofString())
    JsonNode body = JsonUtil.fromJson(response.body(), JsonNode.class)

    then: "url is derived from termx.api-url (application-test.yml), not the static resource file"
    body.get("implementation").get("url").asText() == "http://localhost:8200/api/fhir"
    body.get("url").asText() == "http://localhost:8200/api/fhir/metadata"

    and: "kefhir copies implementation.url into the OpenAPI servers block, so /fhir-swagger follows it"
    def swagger = client.execute(client.builder("/fhir-swagger").GET().build(), BodyHandlers.ofString())
    swagger.body().contains("url: http://localhost:8200/api/fhir")
  }
}
