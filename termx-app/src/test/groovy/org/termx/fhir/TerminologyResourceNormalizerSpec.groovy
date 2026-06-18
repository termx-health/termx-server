package org.termx.fhir

import com.kodality.commons.util.JsonUtil
import com.kodality.kefhir.core.model.ResourceId
import com.kodality.kefhir.structure.api.ResourceContent
import spock.lang.Specification

class TerminologyResourceNormalizerSpec extends Specification {
  def normalizer = new TerminologyResourceNormalizer()

  private Map normalize(String type, Map resource) {
    def content = new ResourceContent(JsonUtil.toJson(resource), "application/fhir+json")
    normalizer.handle(new ResourceId(type, "x"), content, "update")
    return JsonUtil.fromJson(content.getValue(), Map)
  }

  def "missing status defaults to draft"() {
    expect:
    normalize("CodeSystem", [resourceType: "CodeSystem", url: "http://x", content: "complete"]).status == "draft"
  }

  def "present status is left untouched"() {
    expect:
    normalize("CodeSystem", [resourceType: "CodeSystem", status: "active", content: "complete"]).status == "active"
  }

  def "supplement drops a stated caseSensitive; non-supplement keeps it"() {
    expect:
    !normalize("CodeSystem", [resourceType: "CodeSystem", status: "active", content: "supplement", caseSensitive: true]).containsKey("caseSensitive")
    normalize("CodeSystem", [resourceType: "CodeSystem", status: "active", content: "complete", caseSensitive: true]).caseSensitive == true
  }

  def "definition:lang folds into a language designation and the colon-key is removed"() {
    when:
    def out = normalize("CodeSystem", [
        resourceType: "CodeSystem", status: "active", content: "complete",
        concept     : [[code: "c1", definition: "Erste", "definition:en": "First"]]
    ])
    def concept = out.concept[0]

    then: "the non-standard colon-key is gone, base definition stays, English variant becomes a designation"
    !concept.containsKey("definition:en")
    concept.definition == "Erste"
    concept.designation.find { it.language == "en" && it.use?.code == "definition" }?.value == "First"
  }

  def "non-terminology resource types are left untouched"() {
    given:
    def content = new ResourceContent(JsonUtil.toJson([resourceType: "Patient"]), "application/fhir+json")

    when:
    normalizer.handle(new ResourceId("Patient", "x"), content, "update")

    then: "no status injected"
    !JsonUtil.fromJson(content.getValue(), Map).containsKey("status")
  }
}
