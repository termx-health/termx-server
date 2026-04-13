package org.termx.terminology.terminology.codesystem.version

import jakarta.inject.Provider
import org.termx.core.ts.UcumSearchCacheInvalidator
import org.termx.terminology.terminology.codesystem.CodeSystemRepository
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.terminology.terminology.valueset.ValueSetCodeSystemImpactService
import org.termx.ts.PublicationStatus
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemVersion
import spock.lang.Specification

class CodeSystemVersionServiceTest extends Specification {
  def repository = Mock(CodeSystemVersionRepository)
  def entityVersionService = Mock(CodeSystemEntityVersionService)
  def impactServiceProvider = Mock(Provider)
  def impactService = Mock(ValueSetCodeSystemImpactService)
  def codeSystemRepository = Mock(CodeSystemRepository)
  def ucumSearchCacheInvalidator = Mock(UcumSearchCacheInvalidator)

  def service = new CodeSystemVersionService(repository, entityVersionService, impactServiceProvider, codeSystemRepository, ucumSearchCacheInvalidator)

  def "activate refreshes dynamic value sets for any activation caller"() {
    given:
    repository.load("gender", "6.0.1") >> new CodeSystemVersion()
        .setCodeSystem("gender")
        .setVersion("6.0.1")
        .setStatus(PublicationStatus.draft)
    impactServiceProvider.get() >> impactService

    when:
    service.activate("gender", "6.0.1")

    then:
    1 * repository.activate("gender", "6.0.1")
    1 * entityVersionService.activate("gender", "6.0.1")
    1 * impactService.refreshDynamicValueSets("gender")
    1 * codeSystemRepository.load("gender") >> null
  }

  def "activate skips refresh when version is already active"() {
    given:
    repository.load("gender", "6.0.1") >> new CodeSystemVersion()
        .setCodeSystem("gender")
        .setVersion("6.0.1")
        .setStatus(PublicationStatus.active)

    when:
    service.activate("gender", "6.0.1")

    then:
    0 * repository.activate(_, _)
    0 * entityVersionService.activate(_, _)
    0 * impactServiceProvider.get()
    0 * codeSystemRepository.load(_)
  }

  def "activate invalidates ucum cache for ucum supplement code systems"() {
    given:
    repository.load("ucum-supplement-lt", "1.0.0") >> new CodeSystemVersion()
        .setCodeSystem("ucum-supplement-lt")
        .setVersion("1.0.0")
        .setStatus(PublicationStatus.draft)
    impactServiceProvider.get() >> impactService
    codeSystemRepository.load("ucum-supplement-lt") >> new CodeSystem().setId("ucum-supplement-lt").setBaseCodeSystem("ucum")

    when:
    service.activate("ucum-supplement-lt", "1.0.0")

    then:
    1 * repository.activate("ucum-supplement-lt", "1.0.0")
    1 * entityVersionService.activate("ucum-supplement-lt", "1.0.0")
    1 * impactService.refreshDynamicValueSets("ucum-supplement-lt")
    1 * ucumSearchCacheInvalidator.invalidate()
  }
}
