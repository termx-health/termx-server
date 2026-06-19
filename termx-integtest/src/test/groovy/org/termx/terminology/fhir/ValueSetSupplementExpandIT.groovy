package org.termx.terminology.fhir

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import com.kodality.zmei.fhir.resource.other.Parameters
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.valueset.ValueSetFhirImportService
import org.termx.terminology.fhir.valueset.operations.ValueSetExpandOperation
import org.termx.terminology.fhir.valueset.operations.ValueSetValidateCodeOperation
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService

/**
 * Issue #47 end-to-end. A CodeSystem supplement adds a Lithuanian ("lt") designation to a base
 * code system's concept; a ValueSet references the supplement via the {@code valueset-supplement}
 * extension (include.system = base, extension = supplement canonical). $expand with
 * {@code displayLanguage=lt} must surface the supplement's Lithuanian display.
 *
 * Exercises the whole chain: supplement FHIR import (baseEntityVersionId linkage), ValueSet import
 * parsing the valueset-supplement extension (PR #207) and resolving the rule to the supplement, and
 * the expansion merging the supplement's own designations.
 */
@MicronautTest(transactional = true)
class ValueSetSupplementExpandIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject ValueSetFhirImportService vsImportService
  @Inject ValueSetVersionService vsVersionService
  @Inject ValueSetVersionConceptService vsConceptService
  @Inject ValueSetExpandOperation vsExpand
  @Inject ValueSetValidateCodeOperation vsValidate

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    // Base first so the supplement import can link baseEntityVersionId to the base concept version.
    csImportService.importCodeSystem(fixture("fhir/supplement/cs-base.json"), "suppl-base")
    csImportService.importCodeSystem(fixture("fhir/supplement/cs-supplement-lt.json"), "suppl-lt")
    vsImportService.importValueSet(fixture("fhir/supplement/vs-supplement.json"), "suppl-vs")
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "the imported valueset-supplement binds the rule to the supplement"() {
    when:
    def rule = vsVersionService.load("suppl-vs", "1.0.0").orElseThrow().ruleSet.rules.first()

    then: "the rule resolved to the supplement code system, with the base recorded separately"
    rule.codeSystemUri == "http://example.org/CodeSystem/suppl-lt"
    rule.codeSystemBaseUri == "http://example.org/CodeSystem/suppl-base"
    rule.codeSystem == "suppl-lt"
  }

  def "\$expand with displayLanguage=lt surfaces the supplement's Lithuanian designation"() {
    when:
    def snapshot = vsConceptService.expand("suppl-vs", "1.0.0", "lt", true)
    def concept = snapshot.expansion.find { it.concept.code == "code1" }

    then: "the concept is expanded with the Lithuanian display from the supplement (issue #47)"
    concept != null
    concept.display.language == "lt"
    concept.display.name == "Gliukozė"
  }

  def "the FHIR \$expand response surfaces the supplement's localized designation to the user (once)"() {
    given: "an \$expand request for displayLanguage=lt with designations included"
    def req = new Parameters().setParameter([
        new ParametersParameter().setName("url").setValueUri("http://example.org/ValueSet/suppl-vs"),
        new ParametersParameter().setName("displayLanguage").setValueCode("lt"),
        new ParametersParameter().setName("includeDesignations").setValueBoolean(true)])

    when:
    def expanded = vsExpand.run(req)
    def contains = expanded.expansion.contains.find { it.code == "code1" }

    then: "the localized display and designation from the supplement are returned to the user"
    contains != null
    contains.display == "Gliukozė"

    and: "the supplement's lt designation appears exactly once (not duplicated by the base+supplement merge)"
    contains.designation.findAll { it.language == "lt" && it.value == "Gliukozė" }.size() == 1
  }

  def "\$validate-code on a supplement-bound value set validates a code under the BASE system"() {
    given: "a validate-code request naming the BASE code system (the value set rule is bound to the supplement)"
    def req = new Parameters().setParameter([
        new ParametersParameter().setName("url").setValueUri("http://example.org/ValueSet/suppl-vs"),
        new ParametersParameter().setName("system").setValueUri("http://example.org/CodeSystem/suppl-base"),
        new ParametersParameter().setName("code").setValueCode("code1")])

    when:
    def resp = vsValidate.run(req)

    then: "the base system matches the member (whose code system is the supplement) via its base code system uri"
    resp.findParameter("result").orElseThrow().valueBoolean
    resp.findParameter("code").orElseThrow().valueCode == "code1"
  }

  def "\$validate-code on a supplement-bound value set also accepts the supplement system and no system"() {
    expect:
    validate(system, "code1").findParameter("result").orElseThrow().valueBoolean

    where:
    system << ["http://example.org/CodeSystem/suppl-lt", null]
  }

  def "\$validate-code on a supplement-bound value set rejects a code not in the value set"() {
    when:
    def resp = validate("http://example.org/CodeSystem/suppl-base", "nope")

    then:
    !resp.findParameter("result").orElseThrow().valueBoolean
  }

  def "inline \$expand auto-discovers a supplement for displayLanguage and surfaces its localized display (http example #16)"() {
    given: "an inline value set referencing ONLY the base code system, expanded with displayLanguage=lt"
    def req = inlineExpand("""
      {"resourceType":"Parameters","parameter":[
        {"name":"displayLanguage","valueCode":"lt"},
        {"name":"includeDesignations","valueBoolean":true},
        {"name":"valueSet","resource":{
          "resourceType":"ValueSet","url":"http://example.org/ValueSet/inline-suppl-auto","status":"active",
          "compose":{"include":[{"system":"http://example.org/CodeSystem/suppl-base"}]}}}]}""")

    when:
    def contains = vsExpand.run(req).expansion.contains.find { it.code == "code1" }

    then: "the lt supplement is auto-loaded by language; its Lithuanian display + designation surface"
    contains != null
    contains.display == "Gliukozė"
    contains.designation.findAll { it.language == "lt" && it.value == "Gliukozė" }.size() == 1
  }

  def "inline \$expand with useSupplement and no displayLanguage keeps the base display but adds the supplement designation (http example #18)"() {
    given: "an inline value set over the BASE, naming the supplement explicitly, NO displayLanguage"
    def req = inlineExpand("""
      {"resourceType":"Parameters","parameter":[
        {"name":"useSupplement","valueCanonical":"http://example.org/CodeSystem/suppl-lt"},
        {"name":"includeDesignations","valueBoolean":true},
        {"name":"valueSet","resource":{
          "resourceType":"ValueSet","url":"http://example.org/ValueSet/inline-suppl-use","status":"active",
          "compose":{"include":[{"system":"http://example.org/CodeSystem/suppl-base"}]}}}]}""")

    when:
    def contains = vsExpand.run(req).expansion.contains.find { it.code == "code1" }

    then: "display stays the base English; the supplement's lt designation is surfaced"
    contains != null
    contains.display == "Glucose"
    contains.designation.findAll { it.language == "lt" && it.value == "Gliukozė" }.size() == 1
  }

  def "inline \$expand with useSupplement surfaces the supplement's designations even when displayLanguage is a different language"() {
    given: "an inline value set over the BASE, naming the lt supplement, but displayLanguage=en (a different language)"
    def req = inlineExpand("""
      {"resourceType":"Parameters","parameter":[
        {"name":"displayLanguage","valueCode":"en"},
        {"name":"useSupplement","valueCanonical":"http://example.org/CodeSystem/suppl-lt"},
        {"name":"includeDesignations","valueBoolean":true},
        {"name":"valueSet","resource":{
          "resourceType":"ValueSet","url":"http://example.org/ValueSet/inline-suppl-otherlang","status":"active",
          "compose":{"include":[{"system":"http://example.org/CodeSystem/suppl-base"}]}}}]}""")

    when:
    def contains = vsExpand.run(req).expansion.contains.find { it.code == "code1" }

    then: "displayLanguage=en only picks the display (base English); the explicitly-named supplement's lt designation still surfaces"
    contains != null
    contains.display == "Glucose"
    contains.designation.findAll { it.language == "lt" && it.value == "Gliukozė" }.size() == 1
  }

  private static Parameters inlineExpand(String json) {
    com.kodality.zmei.fhir.FhirMapper.fromJson(json, Parameters)
  }

  private Parameters validate(String system, String code) {
    def params = [
        new ParametersParameter().setName("url").setValueUri("http://example.org/ValueSet/suppl-vs"),
        new ParametersParameter().setName("code").setValueCode(code)]
    if (system != null) {
      params.add(new ParametersParameter().setName("system").setValueUri(system))
    }
    vsValidate.run(new Parameters().setParameter(params))
  }

  private String fixture(String path) {
    def stream = getClass().getClassLoader().getResourceAsStream(path)
    assert stream != null, "fixture not found: ${path}"
    return new String(stream.readAllBytes()).replace("﻿", "")
  }
}
