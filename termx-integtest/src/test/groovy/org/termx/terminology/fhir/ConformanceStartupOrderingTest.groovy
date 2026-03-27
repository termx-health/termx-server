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
}
