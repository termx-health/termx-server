package org.termx.terminology.fhir.conformance

import spock.lang.Specification

class TxConformanceSetupLoaderTest extends Specification {

  def "isSetupFile accepts setup resources of the kind and rejects test artifacts"() {
    expect:
    TxConformanceSetupLoader.isSetupFile(name, prefix) == expected

    where:
    name                                          | prefix         || expected
    "codesystem-simple.json"                      | "codesystem-"  || true
    "codesystem-noversion.json"                   | "codesystem-"  || true
    "valueset-all.json"                           | "valueset-"    || true
    "valueset-filter-isa.json"                    | "valueset-"    || true
    // test request/response artifacts are NOT content
    "simple-expand-all-request-parameters.json"   | "valueset-"    || false
    "simple-expand-all-response-valueSet.json"    | "valueset-"    || false
    "valueset-all-response.json"                  | "valueset-"    || false
    // wrong kind / wrong extension
    "valueset-all.json"                           | "codesystem-"  || false
    "codesystem-simple.xml"                       | "codesystem-"  || false
  }
}
