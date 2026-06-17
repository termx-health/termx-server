package org.termx.terminology.fhir

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.valueset.ValueSetFhirImportService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService

/**
 * Issue #47 end-to-end. A CodeSystem supplement adds a Lithuanian ("lt") designation to a base
 * code system's concept; a ValueSet references the supplement via the {@code valueset-supplement}
 * extension (include.system = base, extension = supplement canonical). $expand with
 * {@code displayLanguage=lt} must surface the supplement's Lithuanian display.
 *
 * Exercises the whole chain: supplement FHIR import (baseEntityVersionId linkage), ValueSet import
 * parsing the valueset-supplement extension (PR #207) and resolving the rule to the supplement, and
 * the expansion merging the supplement's own designations.
 */
@MicronautTest(transactional = true)
class ValueSetSupplementExpandIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject ValueSetFhirImportService vsImportService
  @Inject ValueSetVersionService vsVersionService
  @Inject ValueSetVersionConceptService vsConceptService

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    // Base first so the supplement import can link baseEntityVersionId to the base concept version.
    csImportService.importCodeSystem(fixture("fhir/supplement/cs-base.json"), "suppl-base")
    csImportService.importCodeSystem(fixture("fhir/supplement/cs-supplement-lt.json"), "suppl-lt")
    vsImportService.importValueSet(fixture("fhir/supplement/vs-supplement.json"), "suppl-vs")
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "the imported valueset-supplement binds the rule to the supplement"() {
    when:
    def rule = vsVersionService.load("suppl-vs", "1.0.0").orElseThrow().ruleSet.rules.first()

    then: "the rule resolved to the supplement code system, with the base recorded separately"
    rule.codeSystemUri == "http://example.org/CodeSystem/suppl-lt"
    rule.codeSystemBaseUri == "http://example.org/CodeSystem/suppl-base"
    rule.codeSystem == "suppl-lt"
  }

  def "\$expand with displayLanguage=lt surfaces the supplement's Lithuanian designation"() {
    when:
    def snapshot = vsConceptService.expand("suppl-vs", "1.0.0", "lt", true)
    def concept = snapshot.expansion.find { it.concept.code == "code1" }

    then: "the concept is expanded with the Lithuanian display from the supplement (issue #47)"
    concept != null
    concept.display.language == "lt"
    concept.display.name == "Gliukozė"
  }

  private String fixture(String path) {
    def stream = getClass().getClassLoader().getResourceAsStream(path)
    assert stream != null, "fixture not found: ${path}"
    return new String(stream.readAllBytes()).replace("﻿", "")
  }
}
