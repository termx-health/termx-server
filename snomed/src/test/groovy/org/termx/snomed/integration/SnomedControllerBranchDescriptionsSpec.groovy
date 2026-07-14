package org.termx.snomed.integration

import org.termx.snomed.client.SnowstormClient
import org.termx.snomed.concept.SnomedConcept
import org.termx.snomed.decriptionitem.SnomedDescriptionItemResponse
import org.termx.snomed.decriptionitem.SnomedDescriptionItemResponse.SnomedDescriptionItem
import org.termx.snomed.decriptionitem.SnomedDescriptionItemSearchParams
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Migrated from tehik fork: findConceptDescriptions() must load the follow-up concept from the
 * requested SNOMED branch (branch + "/") instead of the default branch, so descriptions come
 * from the correct edition. Falls back to the default branch when none is set.
 */
class SnomedControllerBranchDescriptionsSpec extends Specification {
  def client = Mock(SnowstormClient)
  // findConceptDescriptions only collaborates with snowstormClient (2nd constructor arg)
  def controller = new SnomedController(null, client, null, null, null, null, null, null, null, null, null, null, null, null)

  private static SnomedDescriptionItemResponse responseWith(String conceptId) {
    return new SnomedDescriptionItemResponse().setItems([
        new SnomedDescriptionItem().setConcept(new SnomedConcept().setConceptId(conceptId))])
  }

  def "loads descriptions from the requested branch"() {
    given:
    client.findConceptDescriptions(_) >> CompletableFuture.completedFuture(responseWith("123"))
    client.loadConcept(_) >> CompletableFuture.completedFuture(new SnomedConcept())

    when:
    controller.findConceptDescriptions(new SnomedDescriptionItemSearchParams().setBranch("MAIN/EE"))

    then:
    1 * client.loadConcept("MAIN/EE/", "123") >> CompletableFuture.completedFuture(new SnomedConcept())
  }

  def "falls back to the default branch when none is set"() {
    given:
    client.findConceptDescriptions(_) >> CompletableFuture.completedFuture(responseWith("123"))

    when:
    controller.findConceptDescriptions(new SnomedDescriptionItemSearchParams())

    then:
    1 * client.loadConcept("123") >> CompletableFuture.completedFuture(new SnomedConcept())
  }
}
