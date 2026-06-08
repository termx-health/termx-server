package org.termx.terminology.terminology.codesystem

import com.kodality.commons.model.QueryResult
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.core.sys.spacepackage.resource.PackageResourceService
import org.termx.core.sys.spacepackage.version.PackageVersionService
import org.termx.terminology.terminology.association.AssociationTypeService
import org.termx.terminology.terminology.codesystem.association.CodeSystemAssociationService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.terminology.terminology.definedproperty.DefinedPropertyService
import org.termx.terminology.terminology.valueset.ValueSetImportService
import org.termx.ts.PublicationStatus
import org.termx.ts.codesystem.CodeSystemEntityVersion
import org.termx.ts.codesystem.CodeSystemVersion
import org.termx.ts.codesystem.Concept
import org.termx.ts.codesystem.Designation
import spock.lang.Specification

import java.time.OffsetDateTime

/**
 * saveConcepts holds unchanged concepts (no new version) in BOTH modes, and creates/updates a version
 * only when content actually changed (compared via {@link ConceptContentSignature}). This is the
 * customer fix — re-importing an unchanged file no longer churns concept versions.
 */
class CodeSystemImportServiceMergeTest extends Specification {
  def conceptService = Mock(ConceptService)
  def codeSystemService = Mock(CodeSystemService)
  def codeSystemRepository = Mock(CodeSystemRepository)
  def entityPropertyService = Mock(EntityPropertyService)
  def associationTypeService = Mock(AssociationTypeService)
  def codeSystemVersionService = Mock(CodeSystemVersionService)
  def definedPropertyService = Mock(DefinedPropertyService)
  def codeSystemAssociationService = Mock(CodeSystemAssociationService)
  def codeSystemEntityVersionService = Mock(CodeSystemEntityVersionService)
  def packageVersionService = Mock(PackageVersionService)
  def packageResourceService = Mock(PackageResourceService)
  def valueSetImportService = Mock(ValueSetImportService)

  def service = new CodeSystemImportService(
      conceptService, codeSystemService, codeSystemRepository, entityPropertyService, associationTypeService,
      codeSystemVersionService, definedPropertyService, codeSystemAssociationService, codeSystemEntityVersionService,
      packageVersionService, packageResourceService, valueSetImportService)

  def setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
    codeSystemEntityVersionService.findCodeToIdMap(_) >> [:]
  }

  def cleanup() {
    SessionStore.clearLocal()
  }

  def "MERGE: unchanged concept holds its active version (reuses id, no unlink)"() {
    given:
    codeSystemEntityVersionService.query(_) >> new QueryResult([version(999L, PublicationStatus.active, [designation("Apple")])])
    def concept = concept(version(null, PublicationStatus.active, [designation("Apple")]))

    when:
    service.saveConcepts([concept], csVersion(), [], false)

    then:
    concept.versions.first().id == 999L
    0 * codeSystemVersionService.unlinkEntityVersions(_, _)
  }

  def "MERGE: changed concept creates a new version (old active unlinked)"() {
    given:
    codeSystemEntityVersionService.query(_) >> new QueryResult([version(999L, PublicationStatus.active, [designation("Apple")])])
    def concept = concept(version(null, PublicationStatus.active, [designation("Apricot")]))

    when:
    service.saveConcepts([concept], csVersion(), [], false)

    then:
    concept.versions.first().id == null
    1 * codeSystemVersionService.unlinkEntityVersions(50L, [999L])
  }

  def "REPLACE: unchanged concept holds its version (no draft cancellation, no new version)"() {
    given:
    codeSystemEntityVersionService.query(_) >> new QueryResult([version(999L, PublicationStatus.draft, [designation("Apple")])])
    def concept = concept(version(null, PublicationStatus.active, [designation("Apple")]))

    when:
    service.saveConcepts([concept], csVersion(), [], true)

    then:
    concept.versions.first().id == 999L
    0 * codeSystemEntityVersionService.cancelAllDraftVersions(_, _)
  }

  def "REPLACE: changed concept creates a new version and cancels the old draft"() {
    given:
    codeSystemEntityVersionService.query(_) >> new QueryResult([version(999L, PublicationStatus.draft, [designation("Apple")])])
    def concept = concept(version(null, PublicationStatus.active, [designation("Apricot")]))

    when:
    service.saveConcepts([concept], csVersion(), [], true)

    then:
    concept.versions.first().id == null
    1 * codeSystemEntityVersionService.cancelAllDraftVersions(_, _)
  }

  def "MERGE: retiring an active concept creates a new (retired) version, old active unlinked"() {
    given: "content unchanged, but the import marks the concept retired"
    codeSystemEntityVersionService.query(_) >> new QueryResult([version(999L, PublicationStatus.active, [designation("Apple")])])
    def concept = concept(version(null, PublicationStatus.retired, [designation("Apple")]))

    when:
    service.saveConcepts([concept], csVersion(), [], false)

    then:
    concept.versions.first().id == null
    1 * codeSystemVersionService.unlinkEntityVersions(50L, [999L])
  }

  def "MERGE: re-importing an already-retired unchanged concept holds it (no churn)"() {
    given:
    codeSystemEntityVersionService.query(_) >> new QueryResult([version(999L, PublicationStatus.retired, [designation("Apple")])])
    def concept = concept(version(null, PublicationStatus.retired, [designation("Apple")]))

    when:
    service.saveConcepts([concept], csVersion(), [], false)

    then:
    concept.versions.first().id == 999L
    0 * codeSystemVersionService.unlinkEntityVersions(_, _)
  }

  private static CodeSystemVersion csVersion() {
    return new CodeSystemVersion().setId(50L).setCodeSystem("cs").setVersion("1.0.0")
  }

  private static Concept concept(CodeSystemEntityVersion v) {
    def c = new Concept().setCode("A")
    c.setId(1L)
    c.setVersions([v])
    return c
  }

  private static CodeSystemEntityVersion version(Long id, String status, List<Designation> designations) {
    return new CodeSystemEntityVersion()
        .setId(id)
        .setCode("A")
        .setCodeSystemEntityId(1L)
        .setCodeSystem("cs")
        .setStatus(status)
        .setCreated(OffsetDateTime.now())
        .setDesignations(designations)
        .setAssociations([])
  }

  private static Designation designation(String name) {
    return new Designation().setName(name).setLanguage("en").setDesignationType("display").setDesignationTypeId(10L)
  }
}
