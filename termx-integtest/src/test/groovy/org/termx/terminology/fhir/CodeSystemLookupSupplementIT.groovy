package org.termx.terminology.fhir

import com.kodality.kefhir.structure.api.ResourceContent
import com.kodality.zmei.fhir.FhirMapper
import com.kodality.zmei.fhir.resource.other.Parameters
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.codesystem.operations.CodeSystemLookupOperation
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.ts.codesystem.ConceptQueryParams

/**
 * A CodeSystem supplement bound to a LOCAL base code system must merge its designations when requested
 * via the {@code useSupplement} parameter on {@code $lookup} — the same way it does for a SNOMED
 * (external-provider) base. This reproduces the gap on the local repository path in
 * {@link org.termx.terminology.terminology.codesystem.concept.ConceptService#query}.
 *
 * Reuses the {@code suppl-base} / {@code suppl-lt} fixtures: base has concept {@code code1}="Glucose";
 * the supplement adds an {@code lt} designation "Gliukozė" to {@code code1}.
 */
@MicronautTest(transactional = true)
class CodeSystemLookupSupplementIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject CodeSystemLookupOperation csLookup
  @Inject ConceptService conceptService

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    // Base first so the supplement import can link baseEntityVersionId to the base concept version.
    csImportService.importCodeSystem(fixture("fhir/supplement/cs-base.json"), "suppl-base")
    csImportService.importCodeSystem(fixture("fhir/supplement/cs-supplement-lt.json"), "suppl-lt")
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "ConceptService.query with useSupplement merges the local supplement's designation"() {
    given: "a query for the base concept naming the supplement explicitly"
    def params = new ConceptQueryParams()
        .setCodeSystem("suppl-base")
        .setCodeEq("code1")
        .setIncludeSupplement(true)
        .setUseSupplement("http://example.org/CodeSystem/suppl-lt")
    params.limit(1)

    when:
    def concept = conceptService.query(params).findFirst().orElseThrow()
    def designations = concept.versions.collectMany { it.designations ?: [] }

    then: "the supplement's lt designation is merged onto the concept"
    designations.any { it.language == "lt" && it.name == "Gliukozė" }
  }

  def "\$lookup with useSupplement surfaces the local supplement's designation"() {
    given: "a \$lookup request for the base concept naming the supplement"
    def req = new Parameters().setParameter([
        new ParametersParameter().setName("system").setValueUri("http://example.org/CodeSystem/suppl-base"),
        new ParametersParameter().setName("code").setValueCode("code1"),
        new ParametersParameter().setName("useSupplement").setValueCanonical("http://example.org/CodeSystem/suppl-lt")])

    when:
    def resp = FhirMapper.fromJson(csLookup.run(new ResourceContent(FhirMapper.toJson(req), "json")).getValue(), Parameters)
    def designations = resp.parameter.findAll { it.name == "designation" }

    then: "the supplement's lt designation is returned"
    designations.any { d ->
      d.part.find { it.name == "value" }?.valueString == "Gliukozė" &&
          d.part.find { it.name == "language" }?.valueString == "lt"
    }
  }

  private String fixture(String path) {
    def stream = getClass().getClassLoader().getResourceAsStream(path)
    assert stream != null, "fixture not found: ${path}"
    return new String(stream.readAllBytes()).replace("﻿", "")
  }
}
