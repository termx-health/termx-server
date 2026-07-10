package org.termx.snomed.ts

import org.termx.core.ts.CodeSystemProvider
import com.kodality.commons.exception.ApiException
import org.termx.snomed.concept.SnomedConcept
import org.termx.snomed.concept.SnomedConceptSearchParams
import org.termx.snomed.description.SnomedDescription
import org.termx.snomed.integration.SnomedService
import org.termx.snomed.search.SnomedSearchResult
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter
import spock.lang.Specification

/**
 * Filter-operator coverage for the SNOMED expansion path
 * ({@link SnomedValueSetExpandProvider}). SNOMED value sets are not expanded in SQL — each filter
 * is compiled to ECL and pushed down to Snowstorm. This spec mocks {@link SnomedService} and pins
 * the ECL that each operator compiles to, so the operator→ECL contract is regression-guarded
 * without a live Snowstorm.
 *
 * <p>Supported operators and their ECL (see {@code SnomedValueSetExpandProvider#composeEcl}):
 * <ul>
 *   <li>{@code is-a}          → {@code <<value}  (descendant-or-self)</li>
 *   <li>{@code descendent-of} → {@code <value}   (descendants)</li>
 *   <li>{@code child-of}      → {@code <!value}  (direct children)</li>
 *   <li>{@code generalizes}   → {@code >>value}  (ancestor-or-self)</li>
 *   <li>{@code in}            → {@code ^value}    (members of the reference set)</li>
 *   <li>{@code ecl}           → {@code value}     (synthetic operator; value is already ECL, e.g. {@code *})</li>
 * </ul>
 * Operators with no ECL equivalent are rejected with {@code SN301} rather than silently emitting a
 * bare value (issue #197).
 */
class SnomedValueSetExpandProviderSpec extends Specification {
  SnomedService snomedService = Mock()
  CodeSystemProvider codeSystemProvider = Mock()
  SnomedValueSetExpandProvider provider

  def setup() {
    provider = new SnomedValueSetExpandProvider(new SnomedMapper(), snomedService, codeSystemProvider)
  }

  def "paged expansion compiles each supported operator to the expected ECL"() {
    given:
    def rule = ruleWith(filter(operator, "73211009"))

    when:
    provider.ruleExpandPaged(rule, new ValueSetVersion(), "en", null, 0, 10)

    then: "exactly one Snowstorm page query, carrying the compiled ECL"
    1 * snomedService.searchConceptsPage({ SnomedConceptSearchParams p -> p.ecl == expectedEcl }) >>
        new SnomedSearchResult<SnomedConcept>().setItems([]).setTotal(0)

    where:
    operator        || expectedEcl
    "is-a"          || "<<73211009"
    "descendent-of" || "<73211009"
    "child-of"      || "<!73211009"
    "generalizes"   || ">>73211009"
    "in"            || "^73211009"
  }

  def "the synthetic `ecl` operator passes its value through as the ECL verbatim"() {
    given: "the two shapes ValueSetExpandOperation synthesises for `?fhir_vs` and `?fhir_vs=ecl/<expr>`"
    def rule = ruleWith(filter("ecl", value))

    when:
    provider.ruleExpandPaged(rule, new ValueSetVersion(), "en", null, 0, 10)

    then: "composeEcl emits the value unchanged — no SN301, no constraint prefix"
    1 * snomedService.searchConceptsPage({ SnomedConceptSearchParams p -> p.ecl == value }) >>
        new SnomedSearchResult<SnomedConcept>().setItems([]).setTotal(0)

    where:
    value          | _
    "*"            | _   // ?fhir_vs — all concepts
    "<< 363787002" | _   // ?fhir_vs=ecl/<expr> — raw ECL
  }

  def "the `ecl` operator is also honoured on the non-paged (ruleExpand) path and maps concepts"() {
    // composeEcl is reached from two call sites — ruleExpandPaged (above) and
    // filterConcepts via ruleExpand. Exercise the second one too, and assert the
    // whole compile→search→map flow, so `?fhir_vs` can't regress on either path.
    given: "the all-concepts shape `?fhir_vs` → operator ecl, value *"
    def rule = ruleWith(filter("ecl", "*"))

    when:
    def result = provider.ruleExpand(rule, new ValueSetVersion(), "en")

    then: "filterConcepts compiles the verbatim ECL `*` (no SN301) and the concept surfaces"
    1 * snomedService.searchConcepts({ SnomedConceptSearchParams p -> p.ecl == "*" && p.all }) >>
        [new SnomedConcept().setConceptId("404684003").setActive(true)]
    1 * snomedService.loadDescriptions(_, ["404684003"]) >> [
        new SnomedDescription().setConceptId("404684003").setDescriptionId("d1").setLang("en")
            .setTerm("Clinical finding").setTypeId("900000000000013009").setActive(true).setAcceptabilityMap([:])
    ]
    result*.concept*.code == ["404684003"]
    result.first().display.name == "Clinical finding"
  }

  def "the free-text filter and paging are pushed down to Snowstorm"() {
    given:
    def rule = ruleWith(filter("is-a", "73211009"))

    when:
    provider.ruleExpandPaged(rule, new ValueSetVersion(), "en", "diab", 20, 50)

    then:
    1 * snomedService.searchConceptsPage({ SnomedConceptSearchParams p ->
      p.ecl == "<<73211009" && p.term == "diab" && p.offset == 20 && p.limit == 50
    }) >> new SnomedSearchResult<SnomedConcept>().setItems([]).setTotal(0)
  }

  def "non-paged expansion compiles the ECL and maps the returned SNOMED concepts"() {
    given:
    def rule = ruleWith(filter("is-a", "73211009"))

    when:
    def result = provider.ruleExpand(rule, new ValueSetVersion(), "en")

    then: "the full-result search is issued with the compiled ECL"
    1 * snomedService.searchConcepts({ SnomedConceptSearchParams p -> p.ecl == "<<73211009" && p.all }) >>
        [new SnomedConcept().setConceptId("73211009").setActive(true)]
    1 * snomedService.loadDescriptions(_, ["73211009"]) >> [
        new SnomedDescription().setConceptId("73211009").setDescriptionId("d1").setLang("en")
            .setTerm("Diabetes mellitus").setTypeId("900000000000013009").setActive(true).setAcceptabilityMap([:])
    ]

    and: "the SNOMED conceptId surfaces as the expansion member code"
    result*.concept*.code == ["73211009"]
  }

  def "an operator with no ECL equivalent is rejected instead of producing a bare value"() {
    given:
    def rule = ruleWith(filter(operator, "73211009"))

    when:
    provider.ruleExpandPaged(rule, new ValueSetVersion(), "en", null, 0, 10)

    then: "no Snowstorm call is made; the request is rejected"
    0 * snomedService.searchConceptsPage(_)
    def e = thrown(ApiException)
    e.message.contains("SN301") || e.issues*.code.contains("SN301")

    where:
    operator << ["is-not-a", "descendent-leaf", "regex", "=", "not-in", "exists"]
  }

  def "non-paged expansion tolerates a returned concept that has no descriptions"() {
    given:
    def rule = ruleWith(filter("is-a", "73211009"))

    when:
    def result = provider.ruleExpand(rule, new ValueSetVersion(), "en")

    then: "loadDescriptions returns nothing for the concept — must not NPE"
    1 * snomedService.searchConcepts(_) >> [new SnomedConcept().setConceptId("73211009").setActive(true)]
    1 * snomedService.loadDescriptions(_, ["73211009"]) >> []
    noExceptionThrown()
    result*.concept*.code == ["73211009"]
    result.first().display == null
  }

  // --- helpers ---------------------------------------------------------------

  private static ValueSetVersionRule ruleWith(ValueSetRuleFilter filter) {
    return new ValueSetVersionRule().setFilters([filter])
  }

  private static ValueSetRuleFilter filter(String operator, String value) {
    return new ValueSetRuleFilter().setOperator(operator).setValue(value)
  }
}
