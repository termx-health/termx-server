package org.termx.auth

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import spock.lang.Specification
import spock.lang.Unroll

class YupiSessionProviderTest extends Specification {

  def "default privileges are returned when override is null"() {
    given:
    def provider = new YupiSessionProvider(null)

    expect:
    provider.authenticate(yupiRequest()).privileges == YupiSessionProvider.DEFAULT_PRIVILEGES
  }

  def "default privileges are returned when override is blank or whitespace"() {
    given:
    def provider = new YupiSessionProvider(input)

    expect:
    provider.authenticate(yupiRequest()).privileges == YupiSessionProvider.DEFAULT_PRIVILEGES

    where:
    input << ["", "   ", "\t", ",,,"]
  }

  @Unroll
  def "override #raw produces #expected"() {
    given:
    def provider = new YupiSessionProvider(raw)

    expect:
    provider.authenticate(yupiRequest()).privileges == expected as Set

    where:
    raw                                  | expected
    "*.*.read"                           | ["*.*.read"]
    "*.*.read,*.*.triage"                | ["*.*.read", "*.*.triage"]
    " *.*.read , *.*.triage "            | ["*.*.read", "*.*.triage"]   // trims whitespace
    "icd-10.CodeSystem.read"             | ["icd-10.CodeSystem.read"]
    "*.*.*"                              | ["*.*.*"]
    "*.*.read,,,*.*.write"               | ["*.*.read", "*.*.write"]    // skips empty tokens
  }

  def "read-only override produces a session that fails the triage check"() {
    given:
    def provider = new YupiSessionProvider("*.*.read")
    def session = provider.authenticate(yupiRequest())

    expect:
    session.privileges == ["*.*.read"] as Set
    !session.hasPrivilege("administrative-gender.CodeSystem.triage")
    !session.hasPrivilege("any-space.Wiki.triage")
    session.hasPrivilege("administrative-gender.CodeSystem.read")
  }

  def "read+triage override produces a session that passes the triage check"() {
    given:
    def provider = new YupiSessionProvider("*.*.read,*.*.triage")
    def session = provider.authenticate(yupiRequest())

    expect:
    session.hasPrivilege("administrative-gender.CodeSystem.read")
    session.hasPrivilege("administrative-gender.CodeSystem.triage")
    !session.hasPrivilege("administrative-gender.CodeSystem.write")
  }

  def "explicit JSON in Bearer header overrides the configured default"() {
    given:
    def provider = new YupiSessionProvider("*.*.read")  // configured = read-only
    def request = Mock(HttpRequest)
    def headers = Mock(HttpHeaders)
    headers.getFirst("Authorization") >>
        Optional.of('Bearer yupi{"username":"adhoc","privileges":["*.*.maintain"]}')
    request.getHeaders() >> headers

    when:
    def session = provider.authenticate(request)

    then:
    session.username == "adhoc"
    session.privileges == ["*.*.maintain"] as Set
  }

  def "non-yupi authorization header returns null"() {
    given:
    def provider = new YupiSessionProvider(null)
    def request = Mock(HttpRequest)
    def headers = Mock(HttpHeaders)
    headers.getFirst("Authorization") >> Optional.of("Bearer some-other-token")
    request.getHeaders() >> headers

    when:
    def session = provider.authenticate(request)

    then:
    session == null
  }

  private HttpRequest<?> yupiRequest() {
    def request = Mock(HttpRequest)
    def headers = Mock(HttpHeaders)
    headers.getFirst("Authorization") >> Optional.of("Bearer yupi")
    request.getHeaders() >> headers
    return request
  }
}
