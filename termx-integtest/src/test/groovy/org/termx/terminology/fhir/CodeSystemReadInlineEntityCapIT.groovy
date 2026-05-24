package org.termx.terminology.fhir

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.codesystem.CodeSystemResourceStorage

/**
 * End-to-end coverage for the inline-entity cap from PR #157.
 *
 * <p>{@code CodeSystemResourceStorageTest} (in {@code :terminology}) verifies the cap with
 * mocks — fast, but it can't catch a regression where the cap stays in the unit-tested
 * code path while some other part of the stack regresses (e.g. a refactor that bypasses
 * {@code loadEntities()} entirely on the read path). This test exercises the *full* path:
 * real Postgres (Testcontainers), real Liquibase schema, real import service, real
 * {@code CodeSystemResourceStorage.load()} reading from the DB.
 *
 * <p>Strategy: override the cap to a small value (5) and seed two CodeSystems —
 * one above the cap (10 concepts) and one below (3). The cap engages on the first
 * and stays out of the way on the second.
 *
 * <p>Not currently a low-heap OOM test — that would need {@code -Xmx128m} JVM forking
 * and 50k+ seeded rows, which is significant infrastructure. Tracked in #161.
 */
@MicronautTest(transactional = true)
@Property(name = "termx.fhir.codesystem.read.max-inline-entities", value = "5")
class CodeSystemReadInlineEntityCapIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject CodeSystemResourceStorage csStorage

  static final ObjectMapper MAPPER = new ObjectMapper()

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "read returns CodeSystem without concept[] when conceptsTotal exceeds the inline cap"() {
    given:
    String csJson = buildCodeSystem("cap-test-cs-large", "1.0.0", 10)

    when: "imported"
    csImportService.importCodeSystem(csJson, "cap-test-cs-large")

    and: "read back through the same code path the FHIR GET uses"
    def resourceVersion = csStorage.load("cap-test-cs-large--1.0.0")
    def fhirJson = MAPPER.readTree(resourceVersion.content.value)

    then: "the response is a valid CodeSystem"
    fhirJson.get("resourceType").asText() == "CodeSystem"

    and: "concept[] is empty because the cap engaged (10 > 5)"
    !fhirJson.has("concept") || fhirJson.get("concept").size() == 0

    and: "count is still present and correct — clients can detect the truncation"
    fhirJson.get("count").asInt() == 10
  }

  def "read returns CodeSystem with full concept[] when conceptsTotal is under the inline cap"() {
    given:
    String csJson = buildCodeSystem("cap-test-cs-small", "1.0.0", 3)

    when:
    csImportService.importCodeSystem(csJson, "cap-test-cs-small")
    def resourceVersion = csStorage.load("cap-test-cs-small--1.0.0")
    def fhirJson = MAPPER.readTree(resourceVersion.content.value)

    then: "concept[] is populated (3 ≤ 5, cap stays out of the way)"
    fhirJson.get("resourceType").asText() == "CodeSystem"
    fhirJson.get("concept").size() == 3
    fhirJson.get("count").asInt() == 3
  }

  /**
   * Synthesise a minimal FHIR CodeSystem JSON with {@code n} concepts named {@code c1..cN}.
   * Mirrors the round-trip fixtures' shape so the import service has everything it needs.
   */
  private static String buildCodeSystem(String id, String version, int n) {
    def concepts = (1..n).collect { i -> /{"code": "c${i}", "display": "C${i}"}/ }.join(",\n    ")
    return """{
      "resourceType": "CodeSystem",
      "id": "${id}",
      "url": "http://termx-test.local/CodeSystem/${id}",
      "version": "${version}",
      "name": "${id.replaceAll('-', '')}",
      "title": "Inline-cap regression CS (${n} concepts)",
      "status": "active",
      "experimental": false,
      "content": "complete",
      "caseSensitive": true,
      "concept": [
        ${concepts}
      ]
    }"""
  }
}
