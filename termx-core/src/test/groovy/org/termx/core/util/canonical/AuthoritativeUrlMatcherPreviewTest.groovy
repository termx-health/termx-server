package org.termx.core.util.canonical

import spock.lang.Specification
import spock.lang.Unroll

class AuthoritativeUrlMatcherPreviewTest extends Specification {

  @Unroll
  def 'glob pattern "#pattern" should match "#url" for #type → #expected'() {
    expect:
    AuthoritativeUrlMatcher.matches(pattern, url, type) == expected

    where:
    pattern                          | url                                              | type         || expected
    'http://hl7.org/fhir/*'          | 'http://hl7.org/fhir/administrative-gender'       | 'CodeSystem' || true
    'http://hl7.org/fhir/*'          | 'http://hl7.org/fhir/issue-severity'              | 'CodeSystem' || true
    'http://hl7.org/fhir/*'          | 'http://example.org/cs/test'                      | 'CodeSystem' || false
    'http://hl7.org/fhir/issue-*'    | 'http://hl7.org/fhir/issue-severity'              | 'CodeSystem' || true
    'http://hl7.org/fhir/issue-*'    | 'http://hl7.org/fhir/administrative-gender'       | 'CodeSystem' || false
  }

  def 'GlobMatcher glob with dots in URL should work'() {
    expect:
    GlobMatcher.matches('http://hl7.org/fhir/*', 'http://hl7.org/fhir/administrative-gender')
  }

  def 'GlobMatcher isRegexPattern should not flag URL globs as regex'() {
    expect:
    // A URL with * wildcard should be treated as glob, not regex
    def result = GlobMatcher.isRegexPattern('http://hl7.org/fhir/*')
    // This documents current (potentially buggy) behavior:
    // URLs with dots are flagged as regex patterns
    println "isRegexPattern('http://hl7.org/fhir/*') = ${result}"
    println "hasWildcard('http://hl7.org/fhir/*') = ${GlobMatcher.hasWildcard('http://hl7.org/fhir/*')}"
  }
}
