package org.termx.core.fhir.etag

import com.kodality.kefhir.rest.model.KefhirRequest
import com.kodality.kefhir.rest.model.KefhirResponse
import com.kodality.kefhir.structure.api.ResourceContent
import com.kodality.kefhir.structure.api.ResourceRepresentation
import com.kodality.kefhir.structure.service.ResourceFormatService
import io.micronaut.http.MediaType
import org.hl7.fhir.r5.model.CodeSystem
import spock.lang.Specification

/**
 * Spec for {@link FhirEtagFilter}. Wires the filter against mocks of
 * {@link ResourceFormatService} so the test stays free of real FHIR serialisation
 * machinery — the hash is computed over whatever bytes the mock returns, which is
 * what we want to validate (the filter's contract is "hash whatever the formatter
 * produces and stamp it on the response").
 */
class FhirEtagFilterSpec extends Specification {

  ResourceFormatService formatService = Mock()
  ResourceRepresentation jsonPresenter = Mock {
    getName() >> "json"
  }
  FhirEtagFilter filter = new FhirEtagFilter(formatService)

  /** Helper — wire a request with the basic shape the filter needs. */
  private KefhirRequest readReq(Map<String, String> headers = [:]) {
    KefhirRequest req = new KefhirRequest()
    req.method = "GET"
    req.path = "/fhir/CodeSystem"
    req.accept = [MediaType.APPLICATION_JSON_TYPE]
    headers.each { k, v -> req.putHeader(k, v) }
    return req
  }

  /** Helper — basic 200-with-Resource response. */
  private KefhirResponse okResp() {
    return new KefhirResponse(200, new CodeSystem().setUrl("http://example.org/cs"))
  }

  def "emits weak ETag on GET 200 with Resource body"() {
    given:
    def req = readReq()
    def resp = okResp()
    formatService.findPresenter([MediaType.APPLICATION_JSON_TYPE.toString()]) >> Optional.of(jsonPresenter)
    formatService.compose(_ as CodeSystem, "json") >> new ResourceContent('{"resourceType":"CodeSystem","url":"http://example.org/cs"}', "application/fhir+json")

    when:
    filter.handleRequest(req)
    filter.handleResponse(resp, req)

    then:
    resp.status == 200
    resp.getHeader("ETag")?.startsWith('W/"')
    resp.getHeader("Cache-Control") == "public, max-age=0, must-revalidate"
  }

  def "returns 304 when If-None-Match matches the computed ETag"() {
    given:
    formatService.findPresenter([MediaType.APPLICATION_JSON_TYPE.toString()]) >> Optional.of(jsonPresenter)
    formatService.compose(_ as CodeSystem, "json") >> new ResourceContent('{"resourceType":"CodeSystem"}', "application/fhir+json")

    // First, probe to capture the ETag the filter would produce.
    def probeReq = readReq()
    def probeResp = okResp()
    filter.handleRequest(probeReq)
    filter.handleResponse(probeResp, probeReq)
    def etag = probeResp.getHeader("ETag")

    // Second request supplies that ETag in If-None-Match.
    def req = readReq("If-None-Match": etag)
    def resp = okResp()

    when:
    filter.handleRequest(req)
    filter.handleResponse(resp, req)

    then:
    resp.status == 304
    resp.body == null
    resp.getHeader("ETag") == etag
  }

  def "wildcard If-None-Match always returns 304"() {
    given:
    formatService.findPresenter([MediaType.APPLICATION_JSON_TYPE.toString()]) >> Optional.of(jsonPresenter)
    formatService.compose(_ as CodeSystem, "json") >> new ResourceContent('{"resourceType":"CodeSystem"}', "application/fhir+json")
    def req = readReq("If-None-Match": "*")
    def resp = okResp()

    when:
    filter.handleRequest(req)
    filter.handleResponse(resp, req)

    then:
    resp.status == 304
    resp.body == null
  }

  def "comma-separated If-None-Match matches any element"() {
    given:
    formatService.findPresenter([MediaType.APPLICATION_JSON_TYPE.toString()]) >> Optional.of(jsonPresenter)
    formatService.compose(_ as CodeSystem, "json") >> new ResourceContent('{"resourceType":"CodeSystem"}', "application/fhir+json")
    def probeReq = readReq()
    def probeResp = okResp()
    filter.handleRequest(probeReq)
    filter.handleResponse(probeResp, probeReq)
    def etag = probeResp.getHeader("ETag")

    def req = readReq("If-None-Match": 'W/"deadbeef-1", ' + etag + ', W/"feedface-2"')
    def resp = okResp()

    when:
    filter.handleRequest(req)
    filter.handleResponse(resp, req)

    then:
    resp.status == 304
  }

  def "different bodies produce different ETags"() {
    given:
    formatService.findPresenter([MediaType.APPLICATION_JSON_TYPE.toString()]) >> Optional.of(jsonPresenter)
    formatService.compose(_ as CodeSystem, "json") >>> [
        new ResourceContent('{"resourceType":"CodeSystem","name":"alpha"}', "application/fhir+json"),
        new ResourceContent('{"resourceType":"CodeSystem","name":"beta"}', "application/fhir+json")
    ]

    when:
    def r1 = okResp()
    def req1 = readReq()
    filter.handleRequest(req1)
    filter.handleResponse(r1, req1)
    def r2 = okResp()
    def req2 = readReq()
    filter.handleRequest(req2)
    filter.handleResponse(r2, req2)

    then:
    r1.getHeader("ETag") != r2.getHeader("ETag")
  }

  def "identical bodies produce identical ETags"() {
    given:
    formatService.findPresenter([MediaType.APPLICATION_JSON_TYPE.toString()]) >> Optional.of(jsonPresenter)
    formatService.compose(_ as CodeSystem, "json") >> new ResourceContent('{"resourceType":"CodeSystem","name":"same"}', "application/fhir+json")

    when:
    def r1 = okResp()
    def req1 = readReq()
    filter.handleRequest(req1)
    filter.handleResponse(r1, req1)
    def r2 = okResp()
    def req2 = readReq()
    filter.handleRequest(req2)
    filter.handleResponse(r2, req2)

    then:
    r1.getHeader("ETag") == r2.getHeader("ETag")
  }

  def "POST passes through untouched"() {
    given:
    def req = new KefhirRequest()
    req.method = "POST"
    req.path = "/fhir/CodeSystem"
    def resp = okResp()

    when:
    filter.handleRequest(req)
    filter.handleResponse(resp, req)

    then:
    resp.getHeader("ETag") == null
    0 * formatService._
  }

  def "non-200 responses get no ETag"() {
    given:
    def req = readReq()
    def resp = new KefhirResponse(404, null)

    when:
    filter.handleRequest(req)
    filter.handleResponse(resp, req)

    then:
    resp.getHeader("ETag") == null
    0 * formatService._
  }

  def "non-Resource body is skipped"() {
    given:
    def req = readReq()
    def resp = new KefhirResponse(200, "plain string body")

    when:
    filter.handleRequest(req)
    filter.handleResponse(resp, req)

    then:
    resp.getHeader("ETag") == null
  }

  def "Pragma and Expires headers are cleared on success"() {
    given:
    formatService.findPresenter([MediaType.APPLICATION_JSON_TYPE.toString()]) >> Optional.of(jsonPresenter)
    formatService.compose(_ as CodeSystem, "json") >> new ResourceContent('{"resourceType":"CodeSystem"}', "application/fhir+json")
    def req = readReq()
    def resp = okResp()
    resp.header("Pragma", "no-cache")
    resp.header("Expires", "0")

    when:
    filter.handleRequest(req)
    filter.handleResponse(resp, req)

    then:
    resp.getHeader("Pragma") == null
    resp.getHeader("Expires") == null
  }

  def "no presenter for client Accept leaves response untouched"() {
    given:
    def req = readReq()
    req.accept = [MediaType.APPLICATION_XML_TYPE] // pretend no xml presenter is registered
    formatService.findPresenter([MediaType.APPLICATION_XML_TYPE.toString()]) >> Optional.empty()
    def resp = okResp()

    when:
    filter.handleRequest(req)
    filter.handleResponse(resp, req)

    then:
    resp.getHeader("ETag") == null
    resp.status == 200
  }
}
