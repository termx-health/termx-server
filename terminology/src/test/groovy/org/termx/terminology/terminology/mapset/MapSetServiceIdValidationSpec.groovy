package org.termx.terminology.terminology.mapset

import com.kodality.commons.exception.ApiException
import org.termx.terminology.terminology.mapset.association.MapSetAssociationService
import org.termx.terminology.terminology.mapset.property.MapSetPropertyService
import org.termx.terminology.terminology.mapset.version.MapSetVersionService
import org.termx.ts.mapset.MapSet
import spock.lang.Specification

/**
 * Migrated from tehik fork KL-104: save() must reject a resource id that does not match
 * the FHIR id regex [A-Za-z0-9\-.]{1,64}, mirroring CodeSystemService (TE119).
 */
class MapSetServiceIdValidationSpec extends Specification {
  def repository = Mock(MapSetRepository)
  def versionService = Mock(MapSetVersionService)
  def propertyService = Mock(MapSetPropertyService)
  def associationService = Mock(MapSetAssociationService)
  def service = new MapSetService(repository, versionService, propertyService, associationService)

  def "save rejects an id violating the resource-id regex"() {
    when:
    service.save(new MapSet().setId("bad id"))

    then:
    def e = thrown(ApiException)
    e.issues*.code.contains("TE119")
    0 * repository.save(_)
  }

  def "save accepts a valid id"() {
    when:
    service.save(new MapSet().setId("valid-id.1"))

    then:
    1 * repository.save(_)
  }
}
