package org.termx.terminology.valueset

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.valueset.ValueSetFhirImportService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.snapshot.ValueSetSnapshotRepository
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService

/**
 * Issue #36 size guard (observability). When an expansion is larger than
 * {@code termx.terminology.snapshot.max-expansion-bytes}, the contents are not cached — but the
 * snapshot row is still written so {@code concepts_total} stays queryable (the FHIR read flow can
 * surface {@code expansion.total}) and the skip is visible, rather than the snapshot silently
 * vanishing. The threshold is dropped to 10 bytes here so any real expansion trips the guard.
 */
@MicronautTest(transactional = true)
@Property(name = "termx.terminology.snapshot.max-expansion-bytes", value = "10")
class ValueSetSnapshotSizeGuardIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject ValueSetFhirImportService vsImportService
  @Inject ValueSetVersionService vsVersionService
  @Inject ValueSetVersionConceptService vsConceptService
  @Inject ValueSetSnapshotRepository snapshotRepository

  static final String VS_ID = "simple-enumerated"
  static final String VS_VERSION = "5.0.0"

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    csImportService.importCodeSystem(fixture("fhir/simple/codesystem-simple.json"), "simple")
    vsImportService.importValueSet(fixture("fhir/simple/valueset-enumerated.json"), VS_ID)
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "an over-threshold expansion is not cached, but the snapshot row + concepts_total are still recorded"() {
    given:
    def versionId = vsVersionService.load(VS_ID, VS_VERSION).orElseThrow().getId()

    when: "the draft version is expanded (the expansion exceeds the 10-byte test threshold)"
    def expansion = vsConceptService.expand(VS_ID, VS_VERSION)

    then: "the expansion itself is still returned to the caller"
    !expansion.isEmpty()

    and: "the contents are NOT cached (load returns no expansion)..."
    snapshotRepository.load(VS_ID, versionId)?.expansion == null

    and: "...yet the snapshot metadata IS recorded, so the concept count stays observable"
    snapshotRepository.loadConceptsTotal(VS_ID, versionId) == expansion.size()
  }

  private String fixture(String path) {
    def stream = getClass().getClassLoader().getResourceAsStream(path)
    assert stream != null, "fixture not found: ${path}"
    return new String(stream.readAllBytes()).replace("﻿", "")
  }
}
