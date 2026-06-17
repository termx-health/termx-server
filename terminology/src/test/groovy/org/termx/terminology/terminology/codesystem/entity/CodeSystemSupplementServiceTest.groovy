package org.termx.terminology.terminology.codesystem.entity

import com.kodality.commons.model.QueryResult
import org.termx.core.ts.UcumSearchCacheInvalidator
import org.termx.terminology.terminology.codesystem.CodeSystemRepository
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams
import spock.lang.Specification

class CodeSystemSupplementServiceTest extends Specification {
  def codeSystemService = Mock(CodeSystemService)
  def conceptService = Mock(ConceptService)
  def codeSystemRepository = Mock(CodeSystemRepository)
  def entityVersionService = Mock(CodeSystemEntityVersionService)
  def versionService = Mock(CodeSystemVersionService)
  def entityPropertyService = Mock(EntityPropertyService)
  def ucumSearchCacheInvalidator = Mock(UcumSearchCacheInvalidator)

  def service = new CodeSystemSupplementService(codeSystemService, conceptService, codeSystemRepository,
      entityVersionService, versionService, entityPropertyService, ucumSearchCacheInvalidator)

  def "(#55) supplementFromIds fetches every selected id without the default page-size cap"() {
    given: "more ids than the default page size (101)"
    def ids = (1L..150L).collect()
    codeSystemService.load("cs") >> Optional.of(new CodeSystem())
    CodeSystemEntityVersionQueryParams captured = null

    when:
    service.supplementFromIds("cs", null, ids)

    then: "the entity-version query is unbounded (all()) so none of the 150 codes are dropped"
    1 * entityVersionService.query(_) >> { args -> captured = args[0] as CodeSystemEntityVersionQueryParams; QueryResult.empty() }
    1 * entityVersionService.batchSave(_, "cs")
    captured.limit == -1
    captured.ids == ids.join(",")
  }
}
