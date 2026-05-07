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
    "*.*.view"                           | ["*.*.view"]
    "*.*.view,*.*.triage"                | ["*.*.view", "*.*.triage"]
    " *.*.view , *.*.triage "            | ["*.*.view", "*.*.triage"]   // trims whitespace
    "icd-10.CodeSystem.view"             | ["icd-10.CodeSystem.view"]
    "*.*.*"                              | ["*.*.*"]
    "*.*.view,,,*.*.edit"                | ["*.*.view", "*.*.edit"]    // skips empty tokens
  }

  def "view-only override produces a session that fails the triage check"() {
    given:
    def provider = new YupiSessionProvider("*.*.view")
    def session = provider.authenticate(yupiRequest())

    expect:
    session.privileges == ["*.*.view"] as Set
    !session.hasPrivilege("administrative-gender.CodeSystem.triage")
    !session.hasPrivilege("any-space.Wiki.triage")
    session.hasPrivilege("administrative-gender.CodeSystem.view")
  }

  def "view+triage override produces a session that passes the triage check"() {
    given:
    def provider = new YupiSessionProvider("*.*.view,*.*.triage")
    def session = provider.authenticate(yupiRequest())

    expect:
    session.hasPrivilege("administrative-gender.CodeSystem.view")
    session.hasPrivilege("administrative-gender.CodeSystem.triage")
    !session.hasPrivilege("administrative-gender.CodeSystem.edit")
  }

  def "explicit JSON in Bearer header overrides the configured default"() {
    given:
    def provider = new YupiSessionProvider("*.*.view")  // configured = view-only
    def request = Mock(HttpRequest)
    def headers = Mock(HttpHeaders)
    headers.getFirst("Authorization") >>
        Optional.of('Bearer yupi{"username":"adhoc","privileges":["*.*.publish"]}')
    request.getHeaders() >> headers

    when:
    def session = provider.authenticate(request)

    then:
    session.username == "adhoc"
    session.privileges == ["*.*.publish"] as Set
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
