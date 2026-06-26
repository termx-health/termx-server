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
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptRepository
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemEntityVersion
import org.termx.ts.codesystem.CodeSystemQueryParams
import org.termx.ts.codesystem.Concept
import org.termx.ts.codesystem.ConceptQueryParams
import org.termx.ts.codesystem.Designation
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
  def conceptService = Mock(ConceptService)
  def conceptSupplementService = Mock(org.termx.terminology.terminology.codesystem.concept.ConceptSupplementService)
  def codeSystemEntityVersionService = Mock(org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService)
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
      codeSystemService,
      conceptService,
      conceptSupplementService,
      codeSystemEntityVersionService)

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
    // The inline-VS path always consults the supplement service and then iterates the result; give the mock an
    // explicit empty result so the suite doesn't depend on the framework's default for a generic List return.
    conceptSupplementService.mergeSupplementsIntoExpansion(_, _) >> []
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
  // CodeSystem URI, TermX delegates expansion to the internal
  // ConceptService.query(...) — the same API the UI's concept-list endpoint
  // uses. That gives correct displays (from the concept's designations),
  // DB-level pagination, and a pre-paging total — three things the SQL
  // function `value_set_expand_jsonb` does NOT do for system-only includes.
  //
  // The `?fhir_vs` query convention is SNOMED-only, but a bare `?fhir_vs`
  // suffix on a non-SNOMED canonical is leniently stripped so client libraries
  // that always append it (or copy-paste examples) don't 404. The `|<version>`
  // canonical-with-version syntax is also accepted.
  // ---------------------------------------------------------------------------

  def "expand with url matching a stored CodeSystem delegates to ConceptService.query"() {
    given:
    ConceptQueryParams capturedParams = null
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      return p.uri == "http://loinc.org/answer-list"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org/answer-list").tap { setId("loinc-answer-list") }])
          : QueryResult.empty()
    }
    conceptService.query(_) >> { args ->
      capturedParams = args[0] as ConceptQueryParams
      def qr = new QueryResult<Concept>([cncpt("a", "Alpha"), cncpt("b", "Beta"), cncpt("c", "Gamma")])
      qr.meta.total = 3
      return qr
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org/answer-list"))

    when:
    def result = operation.run(req)

    then:
    capturedParams.codeSystem == "loinc-answer-list"
    result.expansion.contains.size() == 3
    result.expansion.contains*.code == ["a", "b", "c"]
  }

  def "expand with url + bare ?fhir_vs strips the suffix and resolves via CS lookup"() {
    given:
    String capturedCs = null
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      capturedCs = p.uri
      return p.uri == "http://loinc.org/answer-list"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org/answer-list").tap { setId("loinc-answer-list") }])
          : QueryResult.empty()
    }
    conceptService.query(_) >> {
      def qr = new QueryResult<Concept>([cncpt("a", "Alpha"), cncpt("b", "Beta")])
      qr.meta.total = 2
      return qr
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org/answer-list?fhir_vs"))

    when:
    def result = operation.run(req)

    then:
    capturedCs == "http://loinc.org/answer-list" // suffix stripped before CS lookup
    result.expansion.contains.size() == 2
  }

  def "expand with parent CodeSystem canonical (http://loinc.org) ?fhir_vs delegates against that CS"() {
    given:
    ConceptQueryParams capturedParams = null
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      return p.uri == "http://loinc.org"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org").tap { setId("loinc") }])
          : QueryResult.empty()
    }
    conceptService.query(_) >> { args ->
      capturedParams = args[0] as ConceptQueryParams
      def qr = new QueryResult<Concept>((0..<5).collect { cncpt("c${it}" as String, "Display ${it}" as String) })
      qr.meta.total = 5
      return qr
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org?fhir_vs"))

    when:
    def result = operation.run(req)

    then:
    capturedParams.codeSystem == "loinc"
    result.expansion.contains.size() == 5
  }

  def "expand with url containing |<version> canonical syntax extracts the version"() {
    given:
    ConceptQueryParams capturedParams = null
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      return p.uri == "http://loinc.org/answer-list"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org/answer-list").tap { setId("loinc-answer-list") }])
          : QueryResult.empty()
    }
    conceptService.query(_) >> { args ->
      capturedParams = args[0] as ConceptQueryParams
      def qr = new QueryResult<Concept>([cncpt("a", "Alpha")])
      qr.meta.total = 1
      return qr
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org/answer-list|2.82?fhir_vs"))

    when:
    def result = operation.run(req)

    then:
    capturedParams.codeSystem == "loinc-answer-list"
    capturedParams.codeSystemVersion == "2.82"
    result.expansion.contains.size() == 1
  }

  def "expand with url + valueSetVersion param pins the include version"() {
    given:
    ConceptQueryParams capturedParams = null
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      return p.uri == "http://loinc.org/answer-list"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org/answer-list").tap { setId("loinc-answer-list") }])
          : QueryResult.empty()
    }
    conceptService.query(_) >> { args ->
      capturedParams = args[0] as ConceptQueryParams
      def qr = new QueryResult<Concept>([cncpt("a", "Alpha")])
      qr.meta.total = 1
      return qr
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org/answer-list"))
        .addParameter(new Parameters.ParametersParameter("valueSetVersion").setValueString("2.82"))

    when:
    def result = operation.run(req)

    then:
    capturedParams.codeSystemVersion == "2.82"
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

  def "inline-VS expand with an empty include does not NPE (compose dropped by NON_EMPTY round-trip)"() {
    // Regression: resolveIncludeVersions null-checks inlineVs.getCompose()/getInclude(),
    // then deep-copies the value set via a FhirMapper JSON round-trip and loops over
    // copy.getCompose().getInclude(). FhirMapper serializes with Include.NON_EMPTY, which
    // strips an empty compose/include — a non-null-but-empty include list (the shape Jackson
    // deserializes `"include":[]` into) collapses the whole compose to nothing, so
    // copy.getCompose() comes back null and the loop threw an NPE, surfaced to the client as
    // "exception during profile validation: null". The fix returns the untouched original when
    // the round-trip drops the compose — there is nothing concrete to resolve.
    given:
    valueSetVersionConceptRepository.expandFromJson(_) >> []

    def inlineVs = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
    inlineVs.setUrl("http://example.org/inline")
    inlineVs.setStatus("active")
    inlineVs.setCompose(new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetCompose()
        .setInclude([] as List))
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("valueSet").setResource(inlineVs))

    when:
    def result = operation.run(req)

    then:
    noExceptionThrown()
    result.expansion.contains == null || result.expansion.contains.isEmpty()
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
  // expansion.total
  //
  // FHIR R5 ValueSet.expansion.total:
  //   "Total number of codes in the expansion. If the number of codes in an
  //    expansion is changed by the parameters supplied, then this should be
  //    the count of codes corresponding to the parameters."
  //
  // The "parameters" here are FILTERS (filter=, activeOnly) that change the
  // set, NOT pagination (offset/count) which only changes the window. So
  // `total` must be the post-filter, **pre-paging** count.
  // ---------------------------------------------------------------------------

  def "inline-VS expand: expansion.total is the full pre-paging count"() {
    given:
    valueSetVersionConceptRepository.expandFromJson(_) >> (0..<20).collect { concept(it) }

    def inlineVs = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
    inlineVs.setUrl("http://example.org/inline")
    inlineVs.setStatus("active")
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("valueSet").setResource(inlineVs))
        .addParameter(new Parameters.ParametersParameter("count").setValueInteger(5))

    when:
    def result = operation.run(req)

    then:
    result.expansion.contains.size() == 5
    result.expansion.total == 20
  }

  def "implicit-CS expand: expansion.total is the full pre-paging count from ConceptService"() {
    // The concept-query API reports the pre-paging total in QueryResult.meta.total.
    // The operation must forward that as expansion.total — NOT the size of the
    // paginated `data` list.
    given:
    ConceptQueryParams capturedParams = null
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      return p.uri == "http://loinc.org/answer-list"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org/answer-list").tap { setId("loinc-answer-list") }])
          : QueryResult.empty()
    }
    conceptService.query(_) >> { args ->
      capturedParams = args[0] as ConceptQueryParams
      // 20 concepts returned (DB applied count=20), but the underlying set is 72656
      def qr = new QueryResult<Concept>((0..<20).collect { cncpt("c${it}" as String, "Display ${it}" as String) })
      qr.meta.total = 72656
      return qr
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org/answer-list?fhir_vs"))
        .addParameter(new Parameters.ParametersParameter("count").setValueInteger(20))

    when:
    def result = operation.run(req)

    then:
    capturedParams.limit == 20 // count pushed down to DB
    result.expansion.contains.size() == 20
    result.expansion.total == 72656 // pre-paging count from QueryResult.meta.total
  }

  def "inline-VS expand: total reflects the post-filter pre-paging count"() {
    // When `filter` removes concepts, `total` should report the filtered count.
    // Pagination (offset/count) then slices that filtered set, but total stays
    // at the post-filter value.
    given:
    valueSetVersionConceptRepository.expandFromJson(_) >> [
        concept(0).tap { it.getConcept().setCode("apple") },
        concept(1).tap { it.getConcept().setCode("apricot") },
        concept(2).tap { it.getConcept().setCode("banana") },
        concept(3).tap { it.getConcept().setCode("blueberry") },
        concept(4).tap { it.getConcept().setCode("cherry") }]

    def inlineVs = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
    inlineVs.setUrl("http://example.org/inline")
    inlineVs.setStatus("active")
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("valueSet").setResource(inlineVs))
        .addParameter(new Parameters.ParametersParameter("filter").setValueString("ap"))
        .addParameter(new Parameters.ParametersParameter("count").setValueInteger(1))

    when:
    def result = operation.run(req)

    then:
    result.expansion.contains.size() == 1
    result.expansion.total == 2 // apple + apricot match the filter, count=1 slices to 1
  }


  // ---------------------------------------------------------------------------
  // expansion.contains[].display
  //
  // FHIR contract: when the source concept carries a display, the expanded
  // ValueSet must surface it on contains[].display. The Java projection in
  // expandInline copies concept.display.name into contains[].display when
  // present — these tests pin that contract so a future refactor doesn't
  // regress it.
  //
  // (Separately, the SQL function `value_set_expand_jsonb` is responsible for
  // populating concept.display in the first place. That contract belongs in
  // an integration test against the real DB, not here.)
  // ---------------------------------------------------------------------------

  def "inline-VS expand: contains[].display is populated from concept's display field"() {
    given:
    valueSetVersionConceptRepository.expandFromJson(_) >> [
        concept(0).tap { it.setDisplay(new org.termx.ts.codesystem.Designation().setName("Male").setLanguage("en")) },
        concept(1).tap { it.setDisplay(new org.termx.ts.codesystem.Designation().setName("Female").setLanguage("en")) },
        concept(2)] // no display

    def inlineVs = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
    inlineVs.setUrl("http://example.org/inline")
    inlineVs.setStatus("active")
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("valueSet").setResource(inlineVs))

    when:
    def result = operation.run(req)

    then:
    result.expansion.contains.size() == 3
    result.expansion.contains[0].display == "Male"
    result.expansion.contains[1].display == "Female"
    result.expansion.contains[2].display == null
  }

  def "implicit-CS expand: contains[].display is picked from the concept's designations"() {
    // ConceptService.query returns Concepts with their versions and designations.
    // The operation uses ConceptUtil.getDisplay(designations, preferredLang, ...)
    // — same helper CodeSystemLookupOperation uses — to pick the display
    // matching the requested language.
    given:
    valueSetService.query(_) >> QueryResult.empty()
    codeSystemService.query(_) >> { args ->
      CodeSystemQueryParams p = args[0]
      return p.uri == "http://loinc.org/answer-list"
          ? new QueryResult<>([new CodeSystem().setUri("http://loinc.org/answer-list").tap { setId("loinc-answer-list") }])
          : QueryResult.empty()
    }
    conceptService.query(_) >> {
      def qr = new QueryResult<Concept>([cncpt("LA29797-0", "Decreased")])
      qr.meta.total = 1
      return qr
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://loinc.org/answer-list"))

    when:
    def result = operation.run(req)

    then:
    result.expansion.contains.size() == 1
    result.expansion.contains[0].code == "LA29797-0"
    result.expansion.contains[0].display == "Decreased"
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
    snomedProvider.ruleExpandPaged(_, _, _, _, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return pagedResult([snomedConcept("404684003", "Clinical finding"),
                          snomedConcept("123037004", "Body structure"),
                          snomedConcept("71388002", "Procedure")])
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
    snomedProvider.ruleExpandPaged(_, _, _, _, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return pagedResult([snomedConcept("409822003", "Domain bacteria"),
                          snomedConcept("1052801005", "Domain bacteria isolate"),
                          snomedConcept("78239009",   "Gram-positive bacterium"),
                          snomedConcept("87172008",   "Gram-negative bacterium")])
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
    snomedProvider.ruleExpandPaged(_, _, _, _, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return pagedResult([snomedConcept("733073007", "OWL axiom reference set"),
                          snomedConcept("900000000000497000", "CTV3 simple map reference set"),
                          snomedConcept("447562003", "ICD-10 complex map reference set")])
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
    snomedProvider.ruleExpandPaged(_, _, _, _, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return pagedResult([snomedConcept("404684003", "Clinical finding (axiom)"),
                          snomedConcept("71388002",  "Procedure (axiom)")])
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
    snomedProvider.ruleExpandPaged(_, _, _, _, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return pagedResult([snomedConcept("363698007", "Finding site"),
                          snomedConcept("116676008", "Associated morphology")])
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

  def "SNOMED ?fhir_vs passes offset+count+filter to ruleExpandPaged and reports the upstream total"() {
    // Regression test for an OOM in production: the previous implementation
    // called ruleExpand (which loops Snowstorm at limit=9999 via setAll(true))
    // and sliced in memory — for ECL=* on a SNOMED edition this materialised the
    // entire concept set into heap before discarding all but `count` items.
    //
    // The contract now is: offset, count, and filter are forwarded to
    // ruleExpandPaged; total comes from the QueryResult (so FHIR
    // expansion.total reflects the real post-ECL count even though only a page
    // of items is materialised).
    given:
    Integer capturedOffset = null
    Integer capturedCount = null
    String capturedFilter = null
    snomedProvider.ruleExpandPaged(_, _, _, _, _, _) >> { args ->
      capturedFilter = args[3] as String
      capturedOffset = args[4] as Integer
      capturedCount = args[5] as Integer
      def page = [snomedConcept("404684003", "Clinical finding"),
                  snomedConcept("123037004", "Body structure")]
      def result = new QueryResult<>(page)
      result.getMeta().setTotal(372656)
      return result
    }

    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://snomed.info/sct?fhir_vs"))
        .addParameter(new Parameters.ParametersParameter("offset").setValueInteger(40))
        .addParameter(new Parameters.ParametersParameter("count").setValueInteger(2))
        .addParameter(new Parameters.ParametersParameter("filter").setValueString("clin"))

    when:
    def result = operation.run(req)

    then:
    capturedOffset == 40
    capturedCount == 2
    capturedFilter == "clin"

    result.expansion.total == 372656
    result.expansion.offset == 40
    result.expansion.contains.size() == 2
  }

  def "SNOMED versioned URL passes the edition/version URI on the rule"() {
    // IG: implementations populating the version element SHOULD use the URI
    // form `http://snomed.info/sct/<sctid>/version/<YYYYMMDD>`. The provider's
    // getBranch(...) parses the URI to a Snowstorm branch, so the operation
    // must carry it through on rule.codeSystemVersion.
    given:
    ValueSetVersionRule capturedRule = null
    snomedProvider.ruleExpandPaged(_, _, _, _, _, _) >> { args ->
      capturedRule = args[0] as ValueSetVersionRule
      return pagedResult([snomedConcept("409822003", "Domain bacteria"),
                          snomedConcept("78239009",  "Gram-positive bacterium")])
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
  // big/* cost & cycle guards (tx-ecosystem 'big' suite)
  //
  // A value set whose compose.include/exclude.valueSet imports form a cycle, or
  // an unbounded expansion that exceeds the cost threshold, must abort with a
  // 4xx OperationOutcome (circular-reference / too-costly) instead of looping or
  // returning a giant 200.
  // ---------------------------------------------------------------------------

  def "expand of a value set with a circular import chain is a 4xx circular-reference error"() {
    given:
    // big-circle-1 imports big-circle-2; big-circle-2 excludes big-circle-1 -> cycle. Both are bundled as
    // tx-resources (as the validator does at runtime), so the import refs resolve and the cycle is detectable.
    def bc1 = composeVs("http://hl7.org/fhir/test/ValueSet/big-circle-1",
        "http://hl7.org/fhir/test/ValueSet/big-circle-2", null)
    def bc2 = composeVs("http://hl7.org/fhir/test/ValueSet/big-circle-2",
        null, "http://hl7.org/fhir/test/ValueSet/big-circle-1")
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("valueSet").setResource(bc1))
        .addParameter(new Parameters.ParametersParameter("tx-resource").setResource(bc1))
        .addParameter(new Parameters.ParametersParameter("tx-resource").setResource(bc2))

    when:
    operation.run(req)

    then:
    def e = thrown(FhirException)
    e.statusCode == 422
    e.issues[0].severity == org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity.ERROR
    e.issues[0].code == org.hl7.fhir.r5.model.OperationOutcome.IssueType.PROCESSING
    e.issues[0].details.coding[0].code == "vs-invalid"
  }

  def "unbounded expand over the cost threshold is a 4xx too-costly error"() {
    given:
    conceptSupplementService.mergeSupplementsIntoExpansion(_, _) >> []
    // 10001 members > the default 10000 limit, and no count param -> unbounded -> too-costly.
    valueSetVersionConceptRepository.expandFromJson(_) >> (0..10000).collect { concept(it) }
    def inlineVs = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
    inlineVs.setUrl("http://example.org/inline")
    inlineVs.setStatus("active")
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("valueSet").setResource(inlineVs))

    when:
    operation.run(req)

    then:
    def e = thrown(FhirException)
    e.statusCode == 422
    e.issues[0].code == org.hl7.fhir.r5.model.OperationOutcome.IssueType.TOOCOSTLY
  }

  def "paged expand over the cost threshold returns the page, not too-costly"() {
    given:
    conceptSupplementService.mergeSupplementsIntoExpansion(_, _) >> []
    valueSetVersionConceptRepository.expandFromJson(_) >> (0..10000).collect { concept(it) }
    def inlineVs = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
    inlineVs.setUrl("http://example.org/inline")
    inlineVs.setStatus("active")
    def req = new Parameters()
        .addParameter(new Parameters.ParametersParameter("valueSet").setResource(inlineVs))
        .addParameter(new Parameters.ParametersParameter("count").setValueInteger(50))

    when:
    def result = operation.run(req)

    then:
    result.expansion.contains.size() == 50
    result.expansion.total == 10001
  }

  /** A zmei ValueSet whose compose imports/excludes another value set by url (for cycle tests). */
  private static com.kodality.zmei.fhir.resource.terminology.ValueSet composeVs(String url, String includeRef, String excludeRef) {
    def vs = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
    vs.setUrl(url)
    vs.setVersion("5.0.0")
    vs.setStatus("active")
    def compose = new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetCompose()
    if (includeRef != null) {
      compose.setInclude([new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude().setValueSet([includeRef])])
    }
    if (excludeRef != null) {
      compose.setExclude([new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude().setValueSet([excludeRef])])
    }
    vs.setCompose(compose)
    return vs
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

  /**
   * Build a real `Concept` (not a ValueSetVersionConcept) for tests that mock
   * the concept-query API. The first version carries a single "display"
   * designation in English — matches what the production code expects from
   * ConceptService.query when displayLanguage is unset or "en".
   */
  private static Concept cncpt(String code, String displayName) {
    return new Concept().tap { c ->
      c.setCode(code)
      c.setVersions([new CodeSystemEntityVersion().setDesignations([
          new Designation().setName(displayName).setLanguage("en").setDesignationType("display")
      ])])
    }
  }

  /**
   * Wrap a list as a QueryResult whose total matches the list size. Use for SNOMED
   * provider stubs where the test doesn't care about the upstream-vs-page-size
   * distinction (one-page result, total = items in that page).
   */
  private static QueryResult<ValueSetVersionConcept> pagedResult(List<ValueSetVersionConcept> concepts) {
    def r = new QueryResult<ValueSetVersionConcept>(concepts)
    r.getMeta().setTotal(concepts.size())
    return r
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
    // No displayLanguage requested, so the operation falls back to the value set version's preferredLanguage ("en").
    valueSetVersionConceptService.expand("vs1", "1.0.0", "en", false) >> snapshot
    provenanceService.find("ValueSetVersion|7") >> []
  }
}
