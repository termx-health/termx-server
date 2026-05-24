package org.termx.terminology.fhir.codesystem

import com.kodality.commons.model.QueryResult
import org.termx.core.sys.provenance.ProvenanceService
import org.termx.terminology.terminology.codesystem.CodeSystemImportService
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemEntityVersion
import org.termx.ts.codesystem.CodeSystemVersion
import spock.lang.Specification

/**
 * Asserts that {@link CodeSystemResourceStorage#load} respects the inline-entity cap
 * introduced to stop dev-server from OOMing on FHIR reads of large publishers
 * (LOINC, ICD-10, SNOMED CT). The cap is the only thing standing between a
 * read of a 370k-concept CodeSystem and a JDBC-buffered, heap-killed JVM —
 * regressing it would re-open the original incident, so it gets explicit coverage.
 */
class CodeSystemResourceStorageTest extends Specification {
  static final int MAX_INLINE = 50_000

  def codeSystemService = Mock(CodeSystemService)
  def codeSystemVersionService = Mock(CodeSystemVersionService)
  def codeSystemEntityVersionService = Mock(CodeSystemEntityVersionService)
  def provenanceService = Mock(ProvenanceService)
  def importService = Mock(CodeSystemImportService)
  def mapper = Mock(CodeSystemFhirMapper)

  def storage = new CodeSystemResourceStorage(
      codeSystemService, codeSystemVersionService, codeSystemEntityVersionService,
      provenanceService, importService, mapper, "true", MAX_INLINE)

  def "read returns the CodeSystem without entities when conceptsTotal exceeds the inline cap"() {
    given:
    def cs = new CodeSystem(id: "huge-cs")
    def version = new CodeSystemVersion(
        id: 1L, codeSystem: "huge-cs", version: "1.0", conceptsTotal: MAX_INLINE + 10_000)
    codeSystemService.query(_) >> new QueryResult([cs])
    codeSystemVersionService.load("huge-cs", "1.0") >> Optional.of(version)
    provenanceService.find(_) >> []
    mapper.toFhirJson(_, _, _) >> "{}"

    when:
    storage.load("huge-cs--1.0")

    then:
    // The cap engaged: the heavy entity query was never executed.
    0 * codeSystemEntityVersionService.query(_)
    // The version's entities collection is empty (not null) so downstream
    // JSON serialization produces a valid CodeSystem with no concept[] body.
    version.entities == []
  }

  def "read loads entities normally when conceptsTotal is under the inline cap"() {
    given:
    def cs = new CodeSystem(id: "small-cs")
    def version = new CodeSystemVersion(
        id: 1L, codeSystem: "small-cs", version: "1.0", conceptsTotal: 500)
    codeSystemService.query(_) >> new QueryResult([cs])
    codeSystemVersionService.load("small-cs", "1.0") >> Optional.of(version)
    provenanceService.find(_) >> []
    mapper.toFhirJson(_, _, _) >> "{}"

    when:
    storage.load("small-cs--1.0")

    then:
    // The cap stayed out of the way: entities were loaded.
    1 * codeSystemEntityVersionService.query(_) >> new QueryResult([new CodeSystemEntityVersion()])
    version.entities.size() == 1
  }

  def "read loads entities when conceptsTotal is at the cap exactly (boundary)"() {
    given:
    def cs = new CodeSystem(id: "boundary-cs")
    def version = new CodeSystemVersion(
        id: 1L, codeSystem: "boundary-cs", version: "1.0", conceptsTotal: MAX_INLINE)
    codeSystemService.query(_) >> new QueryResult([cs])
    codeSystemVersionService.load("boundary-cs", "1.0") >> Optional.of(version)
    provenanceService.find(_) >> []
    mapper.toFhirJson(_, _, _) >> "{}"

    when:
    storage.load("boundary-cs--1.0")

    then:
    // The cap is strictly >, not >= — equal to the cap is allowed through.
    1 * codeSystemEntityVersionService.query(_) >> new QueryResult([])
  }

  def "read returns null gracefully when the CodeSystem doesn't exist (no cap interaction)"() {
    given:
    codeSystemService.query(_) >> new QueryResult([])

    when:
    def result = storage.load("missing-cs--1.0")

    then:
    result == null
    0 * codeSystemEntityVersionService.query(_)
  }
}
