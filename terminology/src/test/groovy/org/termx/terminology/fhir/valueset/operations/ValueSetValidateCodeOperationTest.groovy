package org.termx.terminology.fhir.valueset.operations

import com.kodality.commons.model.QueryResult
import com.kodality.zmei.fhir.resource.other.Parameters
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.codesystem.CodeSystemEntityVersion
import org.termx.ts.codesystem.Concept
import org.termx.ts.codesystem.Designation
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionConcept
import org.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue
import spock.lang.Specification

/**
 * Behaviour tests for {@link ValueSetValidateCodeOperation} display validation: a provided display
 * is valid when it matches any of the concept's designations (preferred display or an additional
 * designation), in any language when no displayLanguage is constrained.
 */
class ValueSetValidateCodeOperationTest extends Specification {
  def valueSetVersionService = Mock(ValueSetVersionService)
  def valueSetVersionConceptService = Mock(ValueSetVersionConceptService)
  def conceptService = Mock(ConceptService)
  def expandOperation = Mock(org.termx.terminology.fhir.valueset.operations.ValueSetExpandOperation)

  def operation = new ValueSetValidateCodeOperation(valueSetVersionService, valueSetVersionConceptService, conceptService, expandOperation)

  ValueSetVersion vsVersion = new ValueSetVersion().setValueSet("ehr-feed-category").setVersion("1.0.0")

  def setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
  }

  def cleanup() {
    SessionStore.clearLocal()
  }

  def "accepts a display that matches a designation in a different language than the preferred display"() {
    given: "concept whose preferred display is Estonian 'Allergia' and an additional English designation 'Allergy'"
    valueSetVersionConceptService.expand(vsVersion, _) >> [concept()]

    when: "validating display 'Allergy' without a displayLanguage"
    def resp = operation.run(vsVersion, req("allergy", "Allergy", null))

    then:
    bool(resp, "result")
    !resp.findParameter("message").isPresent()
  }

  def "accepts the preferred display itself"() {
    given:
    valueSetVersionConceptService.expand(vsVersion, _) >> [concept()]

    when:
    def resp = operation.run(vsVersion, req("allergy", "Allergia", null))

    then:
    bool(resp, "result")
  }

  def "rejects a display that matches no designation"() {
    given:
    valueSetVersionConceptService.expand(vsVersion, _) >> [concept()]

    when:
    def resp = operation.run(vsVersion, req("allergy", "Nonsense", null))

    then:
    !bool(resp, "result")
    resp.findParameter("message").get().getValueString() == "The display 'Nonsense' is incorrect"
  }

  def "restricts the match to the requested displayLanguage"() {
    given: "'Allergy' is an English designation"
    valueSetVersionConceptService.expand(vsVersion, _) >> [concept()]

    when: "validating display 'Allergy' but constrained to Estonian"
    def resp = operation.run(vsVersion, req("allergy", "Allergy", "et"))

    then: "no Estonian designation reads 'Allergy', so it is invalid"
    !bool(resp, "result")
  }

  def "accepts an English display when displayLanguage allows English"() {
    given:
    valueSetVersionConceptService.expand(vsVersion, _) >> [concept()]

    when:
    def resp = operation.run(vsVersion, req("allergy", "Allergy", "et,en"))

    then:
    bool(resp, "result")
  }

  def "a null display is always valid"() {
    given:
    valueSetVersionConceptService.expand(vsVersion, _) >> [concept()]

    when:
    def resp = operation.run(vsVersion, req("allergy", null, null))

    then:
    bool(resp, "result")
  }

  def "accepts a display that matches a code system designation not surfaced in the (language-narrowed) expansion"() {
    given: "the expansion only carries et/en designations (value set supports et, en)"
    valueSetVersionConceptService.expand(vsVersion, _) >> [concept()]
    and: "but the concept in the code system also has a Russian designation"
    conceptService.query(_) >> new QueryResult([conceptWithDesignations([
        new Designation().setName("Allergia").setLanguage("et").setDesignationType("display"),
        new Designation().setName("Allergy").setLanguage("en").setDesignationType("display"),
        new Designation().setName("Аллергия").setLanguage("ru").setDesignationType("display")
    ])])

    when: "validating the Russian display without a displayLanguage"
    def resp = operation.run(vsVersion, req("allergy", "Аллергия", null))

    then:
    bool(resp, "result")
    !resp.findParameter("message").isPresent()
  }

  def "an unknown systemVersion degrades gracefully: result false + UNKNOWN_CODESYSTEM_VERSION issue + valid versions"() {
    given: "the code is in the value set, but only under code system version 0.1.0"
    def c = concept()
    c.getConcept().setCodeSystemVersions(["0.1.0"])
    valueSetVersionConceptService.expand(vsVersion, _) >> [c]

    when: "validating with systemVersion 1.0.0, which does not exist"
    def p = new Parameters()
    p.addParameter(new ParametersParameter("code").setValueCode("allergy"))
    p.addParameter(new ParametersParameter("system").setValueUri("https://helex.org/fhir/CodeSystem/ehr-feed-category"))
    p.addParameter(new ParametersParameter("systemVersion").setValueString("1.0.0"))
    def resp = operation.run(vsVersion, p)

    then: "a 200-style graceful response: result false, available version echoed, message + issues + x-caused-by-unknown-system"
    !bool(resp, "result")
    resp.findParameter("version").get().getValueString() == "0.1.0"
    resp.findParameter("message").get().getValueString().contains("version '1.0.0' could not be found")
    resp.findParameter("message").get().getValueString().contains("Valid versions: 0.1.0")
    resp.findParameter("x-caused-by-unknown-system").get().getValueCanonical() == "https://helex.org/fhir/CodeSystem/ehr-feed-category|1.0.0"
    def oo = resp.findParameter("issues").get().getResource()
    oo.getIssue().find { it.code == "not-found" && it.severity == "error" } != null
    oo.getIssue().find { it.code == "invalid" && it.severity == "warning" } != null
  }

  private static ValueSetVersionConcept concept() {
    return new ValueSetVersionConcept()
        .setActive(true)
        .setConcept(new ValueSetVersionConceptValue()
            .setCode("allergy")
            .setCodeSystem("ehr-feed-category")
            .setCodeSystemUri("https://helex.org/fhir/CodeSystem/ehr-feed-category"))
        .setDisplay(new Designation().setName("Allergia").setLanguage("et").setDesignationType("display"))
        .setAdditionalDesignations([
            new Designation().setName("Allergy").setLanguage("en").setDesignationType("display")
        ])
  }

  private static Concept conceptWithDesignations(List<Designation> designations) {
    Concept c = new Concept()
    c.setVersions([new CodeSystemEntityVersion().setDesignations(designations)])
    return c
  }

  private static Parameters req(String code, String display, String displayLanguage) {
    Parameters p = new Parameters()
    p.addParameter(new ParametersParameter("code").setValueCode(code))
    if (display != null) {
      p.addParameter(new ParametersParameter("display").setValueString(display))
    }
    if (displayLanguage != null) {
      p.addParameter(new ParametersParameter("displayLanguage").setValueCode(displayLanguage))
    }
    return p
  }

  private static boolean bool(Parameters resp, String name) {
    return resp.findParameter(name).map(p -> p.getValueBoolean()).orElse(false)
  }
}
