package org.termx.implementationguide.ig

import com.kodality.commons.exception.ApiException
import org.termx.implementationguide.ig.version.ImplementationGuideVersionService
import spock.lang.Specification

/**
 * Migrated from tehik fork KL-104: changeId() must reject a resource id that does not match
 * the FHIR id regex [A-Za-z0-9\-.]{1,64} (IG105).
 */
class ImplementationGuideServiceIdValidationSpec extends Specification {
  def repository = Mock(ImplementationGuideRepository)
  def versionService = Mock(ImplementationGuideVersionService)
  def service = new ImplementationGuideService(repository, versionService)

  def "changeId rejects a new id violating the resource-id regex"() {
    when:
    service.changeId("old-id", "bad id")

    then:
    def e = thrown(ApiException)
    e.issues*.code.contains("IG105")
    0 * repository.changeId(_, _)
  }

  def "changeId accepts a valid new id"() {
    when:
    service.changeId("old-id", "valid-id.1")

    then:
    1 * repository.changeId("old-id", "valid-id.1")
  }
}
