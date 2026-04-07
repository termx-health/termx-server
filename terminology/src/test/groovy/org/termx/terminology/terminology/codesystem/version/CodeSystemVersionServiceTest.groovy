package org.termx.terminology.terminology.codesystem.version

import jakarta.inject.Provider
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.terminology.terminology.valueset.ValueSetCodeSystemImpactService
import org.termx.ts.PublicationStatus
import org.termx.ts.codesystem.CodeSystemVersion
import spock.lang.Specification

class CodeSystemVersionServiceTest extends Specification {
  def repository = Mock(CodeSystemVersionRepository)
  def entityVersionService = Mock(CodeSystemEntityVersionService)
  def impactServiceProvider = Mock(Provider)
  def impactService = Mock(ValueSetCodeSystemImpactService)

  def service = new CodeSystemVersionService(repository, entityVersionService, impactServiceProvider)

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
  }
}
