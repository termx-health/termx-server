package org.termx.terminology.fhir

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Locks {@link FhirVersions} to the reference engine's semantics (org.hl7.fhir.core 6.9.10
 * {@code VersionUtilities}), the implementation behind the tx-ecosystem version suite.
 */
class FhirVersionsSpec extends Specification {

  @Unroll
  def "versionMatches(#criteria, #candidate) == #expected"() {
    expect:
    FhirVersions.versionMatches(criteria, candidate) == expected

    where:
    criteria  | candidate || expected
    "1.x.x"   | "1.0.0"   || true     // wildcard minor/patch match any
    "1.x.x"   | "1.2.0"   || true
    "1.x.x"   | "2.0.0"   || false    // major differs
    "1.0.x"   | "1.0.0"   || true     // patch wildcard
    "1.0.x"   | "1.2.0"   || false    // minor differs
    "1.0.0"   | "1.0.0"   || true     // exact
    "1.0.0"   | "1.2.0"   || false
    "1"       | "1.0.0"   || false    // bare "1" is a literal, NOT a prefix of 1.0.0
    "1"       | "1"       || true
    "*"       | "1.0.0"   || true     // full wildcard
    "1.0?"    | "1.0.0"   || true     // trailing-? makes patch optional
    "1.0?"    | "1.0.9"   || true
    "1.1?"    | "1.0.0"   || false
  }

  @Unroll
  def "isMoreDetailed(#criteria, #candidate) == #expected"() {
    expect:
    FhirVersions.isMoreDetailed(criteria, candidate) == expected

    where:
    criteria | candidate || expected
    "1.x.x"  | "1.0.0"   || true   // concrete coding refines the wildcard include
    "1.0.x"  | "1.0.0"   || true
    "1.x.x"  | "2.0.0"   || false  // doesn't match
    "1"      | "1.0.0"   || false  // literal, not a wildcard
    "1.0.0"  | "1.0.0"   || false  // already concrete
  }

  @Unroll
  def "versionHasWildcards(#v) == #expected"() {
    expect:
    FhirVersions.versionHasWildcards(v) == expected

    where:
    v       || expected
    "1.x.x" || true
    "1.0.x" || true
    "1.0?"  || true
    "1.*"   || true
    "1"     || false
    "1.0.0" || false
  }
}
