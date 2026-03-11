package com.kodality.commons.client;
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.kodality.commons.model.Severity
import org.junit.Rule
import spock.lang.Specification

import java.net.http.HttpResponse
import java.util.concurrent.CompletionException

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig

class HttpClientSpec extends Specification {
  private static final String ISSUES_JSON = """
  [
      {
          "severity": "ERROR",
          "code": "AA100",
          "message": "AA100.1 - Nevedomiy oshipka happened",
          "params": {
              "faultString": "Nevedomiy oshipka happened",
              "faultCode": "AA100.1"
          }
      }
  ]
  """

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort())

  HttpClient client

  def setup() {
    wireMockRule.start()
    client = new HttpClient(wireMockRule.baseUrl())
  }

  def "query params "(Object input, String expected) {
    expect:
    client.toQueryParams(input) == expected

    where:
    input                | expected
    null                 | ""
    new HashMap<>()      | ""
    ["a": "1"]           | "a=1"
    ["a": "1", "b": "2"] | "a=1&b=2"
    ["a": []]            | ""
    ["a": ["1", "2"]]    | "a=1&a=2"
  }

  def "test ok"() {
    given:
    wireMockRule.stubFor(WireMock.get("/").willReturn(
        WireMock.aResponse()
            .withStatus(200)
            .withBody("i win")
    ))

    when:
    def resp = client.GET("/").join()

    then:
    resp.body() == "i win"
  }

  def "test ok bytes"() {
    given:
    byte[] data = [0, 1, 2, 3]
    wireMockRule.stubFor(WireMock.get("/").willReturn(
        WireMock.aResponse()
            .withStatus(200)
            .withBody(data)
    ))

    when:
    def resp = client.execute(
        client.builder("/").GET().build(),
        HttpResponse.BodyHandlers.ofByteArray()
    )

    then:
    resp.body() == data
  }

  def "test error"() {
    given:
    wireMockRule.stubFor(WireMock.get("/").willReturn(
        WireMock.aResponse()
            .withStatus(givenStatus)
    ))

    when:
    client.GET("/").join()

    then:
    def err = thrown(CompletionException)
    def cause = err.cause as HttpClientError
    cause.response.statusCode() == expectedStatus
    cause.issues == []

    where:
    givenStatus | expectedStatus
    400         | 400
    500         | 500
  }

  def "test JSON error with simple get"() {
    given:
    wireMockRule.stubFor(WireMock.get("/").willReturn(
        WireMock.aResponse()
            .withHeader('Content-Type', 'application/json')
            .withStatus(400)
            .withBody(ISSUES_JSON)
    ))

    when:
    client.GET("/").join()

    then:
    def err = thrown(CompletionException)
    def cause = err.cause as HttpClientError
    cause.response.statusCode() == 400
    cause.issues[0].severity == Severity.ERROR;
    cause.issues[0].code == 'AA100'
    cause.issues[0].message == 'AA100.1 - Nevedomiy oshipka happened'
    cause.issues[0].params['faultString'] == 'Nevedomiy oshipka happened'
    cause.issues[0].params['faultCode'] == 'AA100.1'
  }

  def "test JSON error with byte array get"() {
    given:
    wireMockRule.stubFor(WireMock.get("/").willReturn(
        WireMock.aResponse()
            .withHeader('Content-Type', 'application/json')
            .withStatus(400)
            .withBody(ISSUES_JSON)
    ))

    when:
    client.execute(
        client.builder("/").GET().build(),
        HttpResponse.BodyHandlers.ofByteArray()
    )

    then:
    def cause = thrown(HttpClientError)
    cause.response.statusCode() == 400
    cause.issues[0].severity == Severity.ERROR
    cause.issues[0].code == 'AA100'
    cause.issues[0].message == 'AA100.1 - Nevedomiy oshipka happened'
    cause.issues[0].params['faultString'] == 'Nevedomiy oshipka happened'
    cause.issues[0].params['faultCode'] == 'AA100.1'
  }

  def "do not parse body if this is not json"() {
    given:
    wireMockRule.stubFor(WireMock.get("/").willReturn(
        WireMock.aResponse()
            .withHeader('Content-Type', 'application/binary')
            .withStatus(400)
            .withBody(ISSUES_JSON)
    ))

    when:
    client.execute(
        client.builder("/").GET().build(),
        HttpResponse.BodyHandlers.ofByteArray()
    )

    then:
    def cause = thrown(HttpClientError)
    cause.response.statusCode() == 400
    cause.issues.size() == 0
  }
}
