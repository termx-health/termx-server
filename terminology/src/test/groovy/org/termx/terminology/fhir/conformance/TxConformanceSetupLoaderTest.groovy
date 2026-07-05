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

  def "alreadyPresent treats 'version already final/exists' as loaded, not other errors"() {
    expect:
    TxConformanceSetupLoader.alreadyPresent(body) == expected

    where:
    body                                                                                || expected
    '{"issue":[{"details":{"text":"TE104: Version 1.0.0 is already created and final"}}]}' || true
    '{"issue":[{"details":{"text":"TE102: Version 1.0.0 already exists."}}]}'              || true
    '{"issue":[{"details":{"text":"TE110: Code system x does not exist."}}]}'              || false
    '{"issue":[{"details":{"text":"TE111: Value set x does not exist."}}]}'                || false
    null                                                                                  || false
  }

  def "isSetupFile accepts setup resources and rejects test request/response artifacts"() {
    expect:
    TxConformanceSetupLoader.isSetupFile(name) == expected

    where:
    name                                          || expected
    "codesystem-simple.json"                      || true
    "codesystem-noversion.json"                   || true
    "valueset-all.json"                           || true
    "valueset-filter-isa.json"                    || true
    // test request/response artifacts are NOT content
    "simple-expand-all-request-parameters.json"   || false
    "simple-expand-all-response-valueSet.json"    || false
    "valueset-all-response.json"                  || false
    // non-.json is not a setup file
    "codesystem-simple.xml"                       || false
  }
}
