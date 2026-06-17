package org.termx.terminology.valueset

import com.kodality.commons.util.JsonUtil
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.springframework.jdbc.core.JdbcTemplate
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.valueset.ValueSetFhirImportService
import org.termx.terminology.terminology.valueset.compare.ValueSetCompareService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.snapshot.ValueSetSnapshotRepository
import org.termx.terminology.terminology.valueset.snapshot.ValueSetSnapshotService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionRepository
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.PublicationStatus
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionConcept
import org.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue

import javax.sql.DataSource
import java.time.LocalDate

/**
 * Issue #36 — the value set expansion is stored as gzip-compressed bytea ({@code expansion_bytea})
 * rather than a single jsonb capped near 1 GB. This pins: new snapshots are written compressed and
 * read back (directly and via the version-load hot path that inlines the snapshot), legacy
 * uncompressed jsonb rows still read, and the in-app compare diffs compressed snapshots.
 */
@MicronautTest(transactional = true)
class ValueSetSnapshotStorageIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject ValueSetFhirImportService vsImportService
  @Inject ValueSetVersionService vsVersionService
  @Inject ValueSetVersionConceptService vsConceptService
  @Inject ValueSetSnapshotService snapshotService
  @Inject ValueSetSnapshotRepository snapshotRepository
  @Inject ValueSetVersionRepository vsVersionRepository
  @Inject ValueSetCompareService compareService
  @Inject @Named("default") DataSource dataSource

  static final String VS_ID = "simple-enumerated"
  static final String VS_VERSION = "5.0.0"

  JdbcTemplate jdbc

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    jdbc = new JdbcTemplate(dataSource)
    csImportService.importCodeSystem(fixture("fhir/simple/codesystem-simple.json"), "simple")
    vsImportService.importValueSet(fixture("fhir/simple/valueset-enumerated.json"), VS_ID)
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "a draft expand stores the snapshot gzip-compressed and reads it back across both paths"() {
    given:
    def versionId = vsVersionService.load(VS_ID, VS_VERSION).orElseThrow().getId()

    when: "the draft version is expanded (which persists the snapshot)"
    vsConceptService.expand(VS_ID, VS_VERSION)

    then: "the snapshot row stores the expansion in bytea, with the legacy jsonb column left empty"
    def cols = jdbc.queryForMap(
        "select (expansion is null) as exp_null, (expansion_bytea is not null) as gz_present " +
            "from terminology.value_set_snapshot where sys_status = 'A' and value_set_version_id = ?", versionId)
    cols.exp_null == true
    cols.gz_present == true

    and: "loading the snapshot decompresses it"
    def snapshot = snapshotRepository.load(VS_ID, versionId)
    snapshot.expansion*.concept.code.containsAll(["code1", "code3"])

    and: "the version-load path (which inlines the snapshot) also returns the decompressed expansion"
    def reloaded = vsVersionRepository.load(VS_ID, VS_VERSION)
    reloaded.snapshot != null
    reloaded.snapshot.expansion*.concept.code.containsAll(["code1", "code3"])
  }

  def "a legacy uncompressed jsonb snapshot is still readable"() {
    given: "an expanded snapshot rewritten to the pre-migration shape (jsonb set, bytea null)"
    def versionId = vsVersionService.load(VS_ID, VS_VERSION).orElseThrow().getId()
    vsConceptService.expand(VS_ID, VS_VERSION)
    def json = JsonUtil.toJson(snapshotRepository.load(VS_ID, versionId).expansion)
    jdbc.update("update terminology.value_set_snapshot set expansion = ?::jsonb, expansion_bytea = null " +
        "where sys_status = 'A' and value_set_version_id = ?", json, versionId)

    expect: "the reader falls back to the jsonb column"
    snapshotRepository.load(VS_ID, versionId).expansion*.concept.code.containsAll(["code1", "code3"])
    vsVersionRepository.load(VS_ID, VS_VERSION).snapshot.expansion*.concept.code.containsAll(["code1", "code3"])
  }

  def "compare diffs two compressed snapshots"() {
    given: "two draft versions with hand-built expansions over different code sets"
    def srcId = createVersion("cmp-src", ["code1", "code2"])
    def tgtId = createVersion("cmp-tgt", ["code2", "code3"])

    when:
    def result = compareService.compare(srcId, tgtId)

    then: "code1 dropped, code3 added"
    result.deleted.contains("code1")
    result.added.contains("code3")
    !result.deleted.contains("code2")
    !result.added.contains("code2")
  }

  private Long createVersion(String version, List<String> codes) {
    vsVersionService.save(new ValueSetVersion().setValueSet(VS_ID).setVersion(version).setStatus(PublicationStatus.draft).setReleaseDate(LocalDate.now()))
    def id = vsVersionService.load(VS_ID, version).orElseThrow().getId()
    snapshotService.createSnapshot(VS_ID, id, codes.collect {
      new ValueSetVersionConcept().setActive(true)
          .setConcept(new ValueSetVersionConceptValue().setCode(it).setCodeSystem("simple"))
    })
    return id
  }

  private String fixture(String path) {
    def stream = getClass().getClassLoader().getResourceAsStream(path)
    assert stream != null, "fixture not found: ${path}"
    return new String(stream.readAllBytes()).replace("﻿", "")
  }
}
