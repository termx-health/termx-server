package org.termx.core.util.canonical

import spock.lang.Specification
import spock.lang.Unroll

class AuthoritativeUrlMatcherTest extends Specification {

  @Unroll
  def 'matches "#pattern" against "#url" for #type should be #expected'() {
    expect:
    AuthoritativeUrlMatcher.matches(pattern, url, type) == expected

    where:
    pattern                                        | url                                          | type           || expected
    '*'                                            | 'https://example.org/CodeSystem/test'         | 'CodeSystem'   || true
    'https://example.org/CodeSystem/test'           | 'https://example.org/CodeSystem/test'         | 'CodeSystem'   || true
    'https://example.org/CodeSystem/test'           | 'https://example.org/CodeSystem/other'        | 'CodeSystem'   || false
    'https://example.org/CodeSystem/*'              | 'https://example.org/CodeSystem/test'         | 'CodeSystem'   || true
    'https://example.org/CodeSystem/*'              | 'https://example.org/ValueSet/test'           | 'CodeSystem'   || false
    'https://example\\.org/.*'                      | 'https://example.org/CodeSystem/test'         | 'CodeSystem'   || true
    'https://example\\.org/.*'                      | 'https://other.org/CodeSystem/test'           | 'CodeSystem'   || false
    null                                           | 'https://example.org/CodeSystem/test'         | 'CodeSystem'   || false
    'https://example.org/CodeSystem/test'           | null                                         | 'CodeSystem'   || false
    ''                                             | 'https://example.org/CodeSystem/test'         | 'CodeSystem'   || false
  }

  def 'type collection endpoint matches children'() {
    expect:
    AuthoritativeUrlMatcher.matches('https://example.org/fhir/CodeSystem', 'https://example.org/fhir/CodeSystem/my-cs', 'CodeSystem')
  }

  def 'type collection endpoint does not match unrelated resources'() {
    expect:
    !AuthoritativeUrlMatcher.matches('https://example.org/fhir/CodeSystem', 'https://example.org/fhir/ValueSet/my-vs', 'CodeSystem')
  }
}
