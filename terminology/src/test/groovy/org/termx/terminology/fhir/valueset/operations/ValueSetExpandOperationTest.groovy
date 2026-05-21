package org.termx.terminology.fhir.valueset.operations

import com.kodality.commons.model.QueryResult
import com.kodality.kefhir.core.exception.FhirException
import com.kodality.zmei.fhir.resource.other.Parameters
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.core.sys.provenance.ProvenanceService
import org.termx.core.ts.ValueSetExternalExpandProvider
import org.termx.terminology.fhir.valueset.ValueSetFhirMapper
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptRepository
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemQueryParams
import org.termx.ts.valueset.ValueSet
import org.termx.ts.valueset.ValueSetSnapshot
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionConcept
import org.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import spock.lang.Specification

/**
 * Behaviour tests for {@link ValueSetExpandOperation}, covering:
 * <ul>
 *   <li>Stored-VS path — {@code offset}/{@code count}/{@code filter} parameters
 *       must propagate into the {@link ValueSetSnapshot} handed to
 *       {@link ValueSetFhirMapper#toFhir}.</li>
 *   <li>Inline-VS path — same paging semantics, response built directly without
 *       the mapper.</li>
 *   <li>404 on unresolved {@code url} — documents the absence of an implicit
 *       CodeSystem-as-ValueSet fallback.</li>
 * </ul>
 */
class ValueSetExpandOperationTest extends Specification {
  def valueSetService = Mock(ValueSetService)
  def provenanceService = Mock(ProvenanceService)
  def valueSetVersionService = Mock(ValueSetVersionService)
  def valueSetVersionConceptService = Mock(ValueSetVersionConceptService)
  def valueSetVersionConceptRepository = Mock(ValueSetVersionConceptRepository)
  def mapper = Mock(ValueSetFhirMapper)
  def codeSystemService = Mock(CodeSystemService)
  // SNOMED provider — recognised via getCodeSystemId() == "snomed-ct". Delegated
  // to by the operation for `?fhir_vs[=...]` URLs. The mock captures the
  // synthesised rule so tests can assert on the filter (operator + value) the
  // operation derived from the URL.
  def snomedProvider = Mock(ValueSetExternalExpandProvider) {
    getCodeSystemId() >> "snomed-ct"
  }

  def operation = new ValueSetExpandOperation(
      valueSetService,
      provenanceService,
      valueSetVersionService,
      valueSetVersionConceptService,
      valueSetVersionConceptRepository,
      mapper,
      [snomedProvider],
      codeSystemService)

  /**
   * Snapshot the operation hands off to {@link ValueSetFhirMapper#toFhir}.
   * Captured by the mocked mapper. For the inline-VS path the operation
   * builds the response itself and this stays null.
   */
  ValueSetSnapshot capturedSnapshot

  def setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
    // Capture the snapshot the operation hands off, return a stub so the caller
    // can keep going.
    mapper.toFhir(_, _, _, _ as ValueSetSnapshot, _) >> { args ->
      capturedSnapshot = args[3] as ValueSetSnapshot
      return new com.kodality.zmei.fhir.resource.terminology.ValueSet()
    }
  }

  def cleanup() {
    SessionStore.clearLocal()
  }


  // ---------------------------------------------------------------------------
  // Stored-VS path: $expand?url=<stored-VS.uri>
  // ---------------------------------------------------------------------------

  def "stored-VS expand without offset/count hands full snapshot to mapper"() {
    given:
    stubStoredValueSet(50)
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://example.org/vs"))

    when:
    operation.run(req)

    then:
    capturedSnapshot.expansion.size() == 50
    capturedSnapshot.expansion[0].concept.code == "code_0"
    capturedSnapshot.expansion[49].concept.code == "code_49"
  }

  def "stored-VS expand with count=10 paginates the snapshot to 10 items"() {
    given:
    stubStoredValueSet(100)
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://example.org/vs"))
        .addParameter(new Parameters.ParametersParameter("count").setValueInteger(10))

    when:
    operation.run(req)

    then:
    capturedSnapshot.expansion.size() == 10
    capturedSnapshot.expansion[0].concept.code == "code_0"
    capturedSnapshot.expansion[9].concept.code == "code_9"
  }

  def "stored-VS expand with offset=20 count=5 returns slice starting at offset"() {
    given:
    stubStoredValueSet(100)
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://example.org/vs"))
        .addParameter(new Parameters.ParametersParameter("offset").setValueInteger(20))
        .addParameter(new Parameters.ParametersParameter("count").setValueInteger(5))

    when:
    operation.run(req)

    then:
    capturedSnapshot.expansion.size() == 5
    capturedSnapshot.expansion[0].concept.code == "code_20"
    capturedSnapshot.expansion[4].concept.code == "code_24"
  }

  def "stored-VS expand with offset=10 (no count) skips first 10 items"() {
    given:
    stubStoredValueSet(30)
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://example.org/vs"))
        .addParameter(new Parameters.ParametersParameter("offset").setValueInteger(10))

    when:
    operation.run(req)

    then:
    capturedSnapshot.expansion.size() == 20
    capturedSnapshot.expansion[0].concept.code == "code_10"
    capturedSnapshot.expansion[19].concept.code == "code_29"
  }


  // ---------------------------------------------------------------------------
  // Implicit ValueSet over a stored CodeSystem
  //
  // When `url` doesn't match any stored ValueSet but DOES match a stored
  // CodeSystem URI, TermX synthesises an inline ValueSet whose
  // `compose.include[].system` (and optionally `.version`) point at the
  // CodeSystem, then routes through the existing `expandFromJson` SQL path.
  // The `?fhir_vs` query convention is SNOMED-only, but a bare `?fhir_vs`
  // suffix on a non-SNOMED canonical is leniently stripped so client libraries
  // that always append it (or copy-paste examples) don't 404. The `|<version>`
  // canonical-with-version syntax is also accepted.
  // ---------------------------------------------------------------------------

  def "expand with url matching a stored CodeSystem synthesises inline VS over that CS"() {
    given:
    String capturedJson = null
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      return p.uri == "http://loinc.org/answer-list"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org/answer-list").tap { setId("loinc-answer-list") }])
          : QueryResult.empty()
    }
    valueSetVersionConceptRepository.expandFromJson(_) >> { args ->
      capturedJson = args[0] as String
      return (0..<3).collect { concept(it) }
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org/answer-list"))

    when:
    def result = operation.run(req)

    then:
    capturedJson != null
    capturedJson.contains("http://loinc.org/answer-list")
    result.expansion.contains.size() == 3
  }

  def "expand with url + bare ?fhir_vs strips the suffix and resolves via CS lookup"() {
    given:
    String capturedJson = null
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      return p.uri == "http://loinc.org/answer-list"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org/answer-list").tap { setId("loinc-answer-list") }])
          : QueryResult.empty()
    }
    valueSetVersionConceptRepository.expandFromJson(_) >> { args ->
      capturedJson = args[0] as String
      return [concept(0), concept(1)]
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org/answer-list?fhir_vs"))

    when:
    def result = operation.run(req)

    then:
    capturedJson.contains("http://loinc.org/answer-list")
    !capturedJson.contains("fhir_vs") // suffix stripped before CS lookup
    result.expansion.contains.size() == 2
  }

  def "expand with parent CodeSystem canonical (http://loinc.org) ?fhir_vs synthesises VS for the parent CS"() {
    given:
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      return p.uri == "http://loinc.org"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org").tap { setId("loinc") }])
          : QueryResult.empty()
    }
    valueSetVersionConceptRepository.expandFromJson(_) >> (0..<5).collect { concept(it) }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org?fhir_vs"))

    when:
    def result = operation.run(req)

    then:
    result.expansion.contains.size() == 5
  }

  def "expand with url containing |<version> canonical syntax extracts the version"() {
    given:
    String capturedJson = null
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      return p.uri == "http://loinc.org/answer-list"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org/answer-list").tap { setId("loinc-answer-list") }])
          : QueryResult.empty()
    }
    valueSetVersionConceptRepository.expandFromJson(_) >> { args ->
      capturedJson = args[0] as String
      return [concept(0)]
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org/answer-list|2.82?fhir_vs"))

    when:
    def result = operation.run(req)

    then:
    capturedJson.contains("http://loinc.org/answer-list")
    capturedJson.contains("2.82") // version forwarded as compose.include[].version
    !capturedJson.contains("|2.82") // pipe stripped from the system URI
    result.expansion.contains.size() == 1
  }

  def "expand with url + valueSetVersion param pins the include version"() {
    given:
    String capturedJson = null
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      return p.uri == "http://loinc.org/answer-list"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org/answer-list").tap { setId("loinc-answer-list") }])
          : QueryResult.empty()
    }
    valueSetVersionConceptRepository.expandFromJson(_) >> { args ->
      capturedJson = args[0] as String
      return [concept(0)]
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org/answer-list"))
        .addParameter(new Parameters.ParametersParameter("valueSetVersion").setValueString("2.82"))

    when:
    def result = operation.run(req)

    then:
    capturedJson.contains("http://loinc.org/answer-list")
    capturedJson.contains("2.82")
    result.expansion.contains.size() == 1
  }

  def "expand with url matching neither stored ValueSet nor stored CodeSystem returns 404"() {
    given:
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> QueryResult.empty()
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://example.com/nothing"))

    when:
    operation.run(req)

    then:
    def e = thrown(FhirException)
    e.statusCode == 404
  }


  // ---------------------------------------------------------------------------
  // Inline-VS path: POST with Parameters.parameter[name=valueSet].resource
  // ---------------------------------------------------------------------------

  def "inline-VS expand without offset/count returns all concepts"() {
    given:
    valueSetVersionConceptRepository.expandFromJson(_) >> (0..<10).collect { concept(it) }

    def inlineVs = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
    inlineVs.setUrl("http://example.org/inline")
    inlineVs.setStatus("active")
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("valueSet").setResource(inlineVs))

    when:
    def result = operation.run(req)

    then:
    result.expansion.contains.size() == 10
    result.expansion.contains[0].code == "code_0"
    result.expansion.contains[9].code == "code_9"
  }

  def "inline-VS expand with offset=2 count=3 returns slice starting at offset"() {
    given:
    valueSetVersionConceptRepository.expandFromJson(_) >> (0..<10).collect { concept(it) }

    def inlineVs = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
    inlineVs.setUrl("http://example.org/inline")
    inlineVs.setStatus("active")
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("valueSet").setResource(inlineVs))
        .addParameter(new Parameters.ParametersParameter("offset").setValueInteger(2))
        .addParameter(new Parameters.ParametersParameter("count").setValueInteger(3))

    when:
    def result = operation.run(req)

    then:
    result.expansion.contains.size() == 3
    result.expansion.contains[0].code == "code_2"
    result.expansion.contains[2].code == "code_4"
    result.expansion.offset == 2
  }

  def "inline-VS expand with count=4 (no offset) returns first 4 concepts"() {
    given:
    valueSetVersionConceptRepository.expandFromJson(_) >> (0..<10).collect { concept(it) }

    def inlineVs = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
    inlineVs.setUrl("http://example.org/inline")
    inlineVs.setStatus("active")
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("valueSet").setResource(inlineVs))
        .addParameter(new Parameters.ParametersParameter("count").setValueInteger(4))

    when:
    def result = operation.run(req)

    then:
    result.expansion.contains.size() == 4
    result.expansion.contains[0].code == "code_0"
    result.expansion.contains[3].code == "code_3"
  }


  // ---------------------------------------------------------------------------
  // SNOMED CT implicit-ValueSet URLs
  //
  // Per the HL7 UTG SNOMED CT page
  // (https://build.fhir.org/ig/HL7/UTG/en/SNOMEDCT.html), every pattern below
  // is layered on top of `http://snomed.info/sct[/<edition>/version/<date>]`
  // and resolves to an implicit ValueSet:
  //
  //   ?fhir_vs                  — all concept ids in the edition/version
  //   ?fhir_vs=isa/<sctid>      — concepts subsumed by <sctid>, **including <sctid> itself**
  //   ?fhir_vs=refset           — all concept ids corresponding to reference sets
  //   ?fhir_vs=refset/<sctid>   — active members of the reference set
  //   ?fhir_vs=ecl/<ecl>        — concepts matching the URI-encoded ECL expression
  //
  // TermX delegates expansion of these URLs to the SNOMED
  // ValueSetExternalExpandProvider (SnomedValueSetExpandProvider), which
  // converts the rule's filter into ECL and calls Snowstorm via SnomedService.
  // These tests pin the URL→filter translation: each pattern must produce a
  // rule with the right operator+value so the existing provider's composeEcl
  // produces the correct ECL string. The mock returns canned concepts so the
  // operation's response wiring can also be asserted.
  // ---------------------------------------------------------------------------

  def "SNOMED ?fhir_vs (all concepts) delegates to provider with ECL `*`"() {
    given:
    ValueSetVersionRule capturedRule = null
    snomedProvider.ruleExpand(_, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return [snomedConcept("404684003", "Clinical finding"),
              snomedConcept("123037004", "Body structure"),
              snomedConcept("71388002", "Procedure")]
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://snomed.info/sct?fhir_vs"))

    when:
    def result = operation.run(req)

    then:
    capturedRule != null
    capturedRule.codeSystem == "snomed-ct"
    capturedRule.type == "include"
    capturedRule.filters.size() == 1
    // "ecl" operator with value "*" — provider's composeEcl falls through and emits "*"
    capturedRule.filters[0].operator == "ecl"
    capturedRule.filters[0].value == "*"

    result.expansion.contains.size() == 3
    result.expansion.contains*.code.toSet() == ["404684003", "123037004", "71388002"].toSet()
  }

  def "SNOMED ?fhir_vs=isa/<code> delegates with is-a filter and includes the anchor"() {
    // IG: "transitive is-a relationship … including the concept itself".
    // composeEcl translates operator "is-a" → ECL prefix "<<" which is
    // descendant-or-self semantics, so the anchor is included by Snowstorm.
    given:
    ValueSetVersionRule capturedRule = null
    snomedProvider.ruleExpand(_, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return [snomedConcept("409822003", "Domain bacteria"),
              snomedConcept("1052801005", "Domain bacteria isolate"),
              snomedConcept("78239009",   "Gram-positive bacterium"),
              snomedConcept("87172008",   "Gram-negative bacterium")]
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url")
            .setValueUrl("http://snomed.info/sct?fhir_vs=isa/409822003"))

    when:
    def result = operation.run(req)

    then:
    capturedRule.filters[0].operator == "is-a"
    capturedRule.filters[0].value == "409822003"

    result.expansion.contains.size() == 4
    result.expansion.contains*.code.contains("409822003") // anchor included per IG
    result.expansion.contains*.code.toSet().containsAll(["1052801005", "78239009", "87172008"])
  }

  def "SNOMED ?fhir_vs=refset delegates with is-a 900000000000455006 (Reference set foundation)"() {
    given:
    ValueSetVersionRule capturedRule = null
    snomedProvider.ruleExpand(_, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return [snomedConcept("733073007", "OWL axiom reference set"),
              snomedConcept("900000000000497000", "CTV3 simple map reference set"),
              snomedConcept("447562003", "ICD-10 complex map reference set")]
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url")
            .setValueUrl("http://snomed.info/sct?fhir_vs=refset"))

    when:
    def result = operation.run(req)

    then:
    capturedRule.filters[0].operator == "is-a"
    capturedRule.filters[0].value == "900000000000455006"

    result.expansion.contains.size() == 3
    result.expansion.contains*.code.contains("733073007")
  }

  def "SNOMED ?fhir_vs=refset/<code> delegates with `in` filter (refset members)"() {
    given:
    ValueSetVersionRule capturedRule = null
    snomedProvider.ruleExpand(_, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return [snomedConcept("404684003", "Clinical finding (axiom)"),
              snomedConcept("71388002",  "Procedure (axiom)")]
    }

    // 733073007 = OWL axiom reference set
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url")
            .setValueUrl("http://snomed.info/sct?fhir_vs=refset/733073007"))

    when:
    def result = operation.run(req)

    then:
    capturedRule.filters[0].operator == "in"
    capturedRule.filters[0].value == "733073007"

    result.expansion.contains.size() == 2
    result.expansion.contains*.code.toSet() == ["404684003", "71388002"].toSet()
  }

  def "SNOMED ?fhir_vs=ecl/<expr> URL-decodes and passes the expression through"() {
    given:
    ValueSetVersionRule capturedRule = null
    snomedProvider.ruleExpand(_, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return [snomedConcept("363698007", "Finding site"),
              snomedConcept("116676008", "Associated morphology")]
    }

    // ECL "<< 363787002" — descendants-or-self of 363787002 (Observable entity).
    // IG mandates URI-encoding of the expression in the query string.
    String ecl = java.net.URLEncoder.encode("<< 363787002", "UTF-8")
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url")
            .setValueUrl("http://snomed.info/sct?fhir_vs=ecl/${ecl}" as String))

    when:
    def result = operation.run(req)

    then:
    capturedRule.filters[0].operator == "ecl"
    capturedRule.filters[0].value == "<< 363787002" // URL-decoded
    result.expansion.contains.size() == 2
  }

  def "SNOMED versioned URL passes the edition/version URI on the rule"() {
    // IG: implementations populating the version element SHOULD use the URI
    // form `http://snomed.info/sct/<sctid>/version/<YYYYMMDD>`. The provider's
    // getBranch(...) parses the URI to a Snowstorm branch, so the operation
    // must carry it through on rule.codeSystemVersion.
    given:
    ValueSetVersionRule capturedRule = null
    snomedProvider.ruleExpand(_, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return [snomedConcept("409822003", "Domain bacteria"),
              snomedConcept("78239009",  "Gram-positive bacterium")]
    }

    // 900000000000207008 = SNOMED International Edition
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url")
            .setValueUrl("http://snomed.info/sct/900000000000207008/version/20240101?fhir_vs=isa/409822003"))

    when:
    def result = operation.run(req)

    then:
    capturedRule.filters[0].operator == "is-a"
    capturedRule.filters[0].value == "409822003"
    capturedRule.codeSystemVersion != null
    capturedRule.codeSystemVersion.uri == "http://snomed.info/sct/900000000000207008/version/20240101"

    result.expansion.contains.size() == 2
    result.expansion.contains*.code.contains("409822003")
  }


  // ---------------------------------------------------------------------------
  // Filter parameter (typeahead) — currently unimplemented at the Java layer
  // ---------------------------------------------------------------------------

  def "stored-VS expand with filter restricts results to matching concepts"() {
    // FHIR $expand `filter` is a free-text typeahead filter applied to the
    // expansion. TermX accepts the parameter but does not apply it — the
    // operation passes the un-filtered snapshot to the mapper unchanged.
    given:
    stubStoredValueSet(5,
        [new ValueSetVersionConceptValue().setCode("apple").setCodeSystem("cs"),
         new ValueSetVersionConceptValue().setCode("apricot").setCodeSystem("cs"),
         new ValueSetVersionConceptValue().setCode("banana").setCodeSystem("cs"),
         new ValueSetVersionConceptValue().setCode("blueberry").setCodeSystem("cs"),
         new ValueSetVersionConceptValue().setCode("cherry").setCodeSystem("cs")])
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://example.org/vs"))
        .addParameter(new Parameters.ParametersParameter("filter").setValueString("ap"))

    when:
    operation.run(req)

    then:
    capturedSnapshot.expansion.size() == 2
    capturedSnapshot.expansion*.concept.code.toSet() == ["apple", "apricot"].toSet()
  }


  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static ValueSetVersionConcept concept(int i) {
    return new ValueSetVersionConcept()
        .setConcept(new ValueSetVersionConceptValue()
            .setCode("code_${i}" as String)
            .setCodeSystem("cs")
            .setCodeSystemUri("http://example.org/cs"))
        .setOrderNumber(i)
  }

  private static ValueSetVersionConcept snomedConcept(String code, String display) {
    return new ValueSetVersionConcept()
        .setConcept(new ValueSetVersionConceptValue()
            .setCode(code)
            .setCodeSystem("snomed-ct")
            .setCodeSystemUri("http://snomed.info/sct"))
        .setDisplay(new org.termx.ts.codesystem.Designation().setName(display).setLanguage("en"))
  }

  private static ValueSetVersionConcept conceptFrom(ValueSetVersionConceptValue value, int order) {
    return new ValueSetVersionConcept().setConcept(value).setOrderNumber(order)
  }

  private void stubStoredValueSet(int totalConcepts) {
    def concepts = (0..<totalConcepts).collect { concept(it) }
    stubStoredValueSetWithConcepts(concepts)
  }

  private void stubStoredValueSet(int totalConcepts, List<ValueSetVersionConceptValue> values) {
    def concepts = []
    values.eachWithIndex { v, i -> concepts << conceptFrom(v, i) }
    stubStoredValueSetWithConcepts(concepts)
  }

  private void stubStoredValueSetWithConcepts(List<ValueSetVersionConcept> concepts) {
    def vs = new ValueSet().setUri("http://example.org/vs")
    vs.setId("vs1")
    def vsv = new ValueSetVersion().setPreferredLanguage("en")
    vsv.setVersion("1.0.0").setValueSet("vs1").setId(7L)
    def snapshot = new ValueSetSnapshot()
        .setExpansion(concepts)
        .setConceptsTotal(concepts.size())

    valueSetService.query(_) >> new QueryResult<>([vs])
    valueSetVersionService.loadLastVersion("vs1") >> vsv
    valueSetVersionConceptService.expand("vs1", "1.0.0", null, false) >> snapshot
    provenanceService.find("ValueSetVersion|7") >> []
  }
}
