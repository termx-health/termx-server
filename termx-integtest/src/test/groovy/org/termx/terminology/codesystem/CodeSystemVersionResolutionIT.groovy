package org.termx.terminology.codesystem

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

/**
 * RED tests for issue #228 — multiple versions of one canonical CodeSystem shipped as SEPARATE resources that
 * share a {@code url} but have distinct ids. TermX has a unique index on {@code code_system.uri}
 * ({@code code_system_ukey}), so the second such CodeSystem fails to import (500), and {@code $lookup} with
 * {@code system}+{@code version} then can't resolve the missing version.
 *
 * <p>Expected behavior (to be implemented): two CodeSystems with one canonical url but distinct ids should be
 * folded into a single canonical resource carrying both versions, and {@code $lookup} by {@code version} should
 * return that version's display. (Same-id multi-version already works — this is the distinct-id case.)
 */
@MicronautTest(transactional = true)
class CodeSystemVersionResolutionIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImport
  @Inject CodeSystemLookupOperation lookup

  static final String CS_URL = "http://termx-test.local/CodeSystem/csvr"

  void setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "two code systems with one canonical url but distinct ids both load and \$lookup resolves by version"() {
    given: "v1 (id csvr-1) A->'Alpha v1'; v2 (id csvr-2, SAME url) A->'Alpha v2'"
    csImport.importCodeSystem(codeSystem("csvr-1", "1.0.0", "Alpha v1"), "csvr-1")
    csImport.importCodeSystem(codeSystem("csvr-2", "2.0.0", "Alpha v2"), "csvr-2")

    expect: "lookup of A resolves the display of the requested version"
    lookupDisplay("1.0.0") == "Alpha v1"
    lookupDisplay("2.0.0") == "Alpha v2"
  }

  private String lookupDisplay(String version) {
    def req = new Parameters().setParameter([
        new ParametersParameter().setName("system").setValueUri(CS_URL),
        new ParametersParameter().setName("code").setValueCode("A"),
        new ParametersParameter().setName("version").setValueString(version)])
    def resp = lookup.run(new ResourceContent(FhirMapper.toJson(req), "json"))
    def params = FhirMapper.fromJson(resp.value, Parameters)
    return params.findParameter("display").map { it.valueString }.orElse(null)
  }

  private static String codeSystem(String id, String version, String display) {
    FhirMapper.toJson([
        resourceType: "CodeSystem", id: id, url: CS_URL, name: "Csvr", title: "Csvr",
        status: "active", content: "complete", version: version,
        concept: [[code: "A", display: display]]])
  }
}
