package org.termx.snomed.ts

import com.kodality.commons.model.QueryResult
import io.micronaut.context.BeanProvider
import org.termx.core.ts.CodeSystemProvider
import org.termx.ts.codesystem.CodeSystemEntityVersion
import org.termx.ts.codesystem.Concept
import org.termx.ts.codesystem.ConceptQueryParams
import org.termx.ts.codesystem.EntityPropertyValue
import org.termx.snomed.integration.SnomedService
import spock.lang.Specification

import java.time.OffsetDateTime

class SnomedCodeSystemProviderSpec extends Specification {
  SnomedMapper snomedMapper = new SnomedMapper()
  SnomedService snomedService = Mock()
  CodeSystemProvider codeSystemProvider = Mock()
  BeanProvider<CodeSystemProvider> codeSystemProviderBean = Mock()

  SnomedCodeSystemProvider provider

  def setup() {
    codeSystemProviderBean.get() >> codeSystemProvider
    // snomed-module code system maps SNOMED moduleIds to Snowstorm branch paths.
    codeSystemProvider.searchConcepts(_ as ConceptQueryParams) >> new QueryResult([
        module("900000000000207008", "MAIN"),
        module("11000181102", "MAIN/SNOMEDCT-EE")
    ])
    provider = new SnomedCodeSystemProvider(snomedMapper, snomedService, codeSystemProviderBean)
  }

  def "derives the edition branch from the version URI and loads the concept from it"() {
    given:
    def params = new ConceptQueryParams()
        .setCodeSystem("snomed-ct")
        .setCode("404684003")
        .setCodeSystemVersion("http://snomed.info/sct/11000181102")

    when:
    provider.searchConcepts(params)

    then: "the EE edition branch is passed to the load"
    1 * snomedService.loadConcepts(["404684003"], "MAIN/SNOMEDCT-EE") >> []
  }

  def "appends the dated branch version when the version URI carries an effectiveTime"() {
    given:
    def params = new ConceptQueryParams()
        .setCodeSystem("snomed-ct")
        .setCode("404684003")
        .setCodeSystemVersion("http://snomed.info/sct/11000181102/version/20260601")

    when:
    provider.searchConcepts(params)

    then:
    1 * snomedService.loadConcepts(["404684003"], "MAIN/SNOMEDCT-EE/2026-06-01") >> []
  }

  def "falls back to the default branch when the version is not a SNOMED edition URI"() {
    given:
    def params = new ConceptQueryParams()
        .setCodeSystem("snomed-ct")
        .setCode("404684003")
        .setCodeSystemVersion(version)

    when:
    provider.searchConcepts(params)

    then: "no branch is derived, so the load uses the default branch"
    1 * snomedService.loadConcepts(["404684003"], null) >> []

    where:
    version << [null, "11000181102", "international-edition"]
  }

  def "falls back to the default branch when the module is unknown"() {
    given:
    def params = new ConceptQueryParams()
        .setCodeSystem("snomed-ct")
        .setCode("404684003")
        .setCodeSystemVersion("http://snomed.info/sct/999999999999")

    when:
    provider.searchConcepts(params)

    then:
    1 * snomedService.loadConcepts(["404684003"], null) >> []
  }

  def "ignores non-SNOMED code systems"() {
    given:
    def params = new ConceptQueryParams().setCodeSystem("loinc").setCode("1234-5")

    when:
    def result = provider.searchConcepts(params)

    then:
    result.data.isEmpty()
    0 * snomedService.loadConcepts(_, _)
  }

  private static Concept module(String moduleId, String branchPath) {
    def version = new CodeSystemEntityVersion()
        .setStatus("active")
        .setCreated(OffsetDateTime.now())
        .setPropertyValues([
            new EntityPropertyValue().setEntityProperty("moduleId").setValue(moduleId),
            new EntityPropertyValue().setEntityProperty("branchPath").setValue(branchPath)
        ])
    def concept = new Concept()
    concept.setVersions([version])
    return concept
  }
}
