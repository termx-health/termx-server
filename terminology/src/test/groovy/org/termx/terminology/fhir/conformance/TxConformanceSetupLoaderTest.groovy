package org.termx.terminology.fhir.conformance

import com.kodality.commons.util.JsonUtil
import spock.lang.Specification

import java.nio.file.Files

class TxConformanceSetupLoaderTest extends Specification {

  def "sanitize strips versionAlgorithm metadata termx cannot import, preserving the rest"() {
    given:
    def f = Files.createTempFile("cs", ".json")
    f.toFile().text = JsonUtil.toJson([
        resourceType          : "CodeSystem",
        id                    : "version",
        url                   : "http://hl7.org/fhir/test/CodeSystem/version",
        versionAlgorithmCoding: [system: "http://hl7.org/fhir/version-algorithm", code: "semver"],
        content               : "complete",
    ])

    when:
    def out = JsonUtil.fromJson(TxConformanceSetupLoader.sanitize(f), Map)

    then: "the unimportable hint is gone; identity and content survive"
    !out.containsKey("versionAlgorithmCoding")
    out.id == "version"
    out.url == "http://hl7.org/fhir/test/CodeSystem/version"
    out.content == "complete"

    cleanup:
    Files.deleteIfExists(f)
  }

  def "sanitize also strips the valueString form of versionAlgorithm"() {
    given:
    def f = Files.createTempFile("cs", ".json")
    f.toFile().text = JsonUtil.toJson([resourceType: "CodeSystem", id: "x", versionAlgorithmString: "semver"])

    when:
    def out = JsonUtil.fromJson(TxConformanceSetupLoader.sanitize(f), Map)

    then:
    !out.containsKey("versionAlgorithmString")
    out.id == "x"

    cleanup:
    Files.deleteIfExists(f)
  }

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
