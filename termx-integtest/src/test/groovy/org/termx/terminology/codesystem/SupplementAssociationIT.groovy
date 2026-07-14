package org.termx.terminology.codesystem

import com.kodality.zmei.fhir.FhirMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.terminology.codesystem.concept.ConceptRepository
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.terminology.terminology.codesystem.entity.CodeSystemSupplementService
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams
import org.termx.ts.codesystem.CodeSystemSupplementRequest
import org.termx.ts.codesystem.ConceptQueryParams

/**
 * KL-94 — an association defined on a BASE concept version must resolve when the query is scoped to a
 * SUPPLEMENT entity version.
 *
 * <p>Model: a supplement does NOT get its own {@code concept} rows. A concept is supplemented by adding an
 * extra {@code code_system_entity_version} on the BASE concept entity — carrying {@code base_entity_version_id}
 * → the base version and linked (membership) to the supplement's code_system_version. Associations
 * ({@code code_system_association}) live only on the BASE version;
 * {@link CodeSystemSupplementService#supplementFromIds} copies the version but strips its associations.
 *
 * <p>{@link ConceptRepository} joins {@code code_system_association} on {@code csev.id} alone. Pinned to the
 * SUPPLEMENT entity version (via {@code codeSystemEntityVersionId}), whose {@code id} is not the association's
 * endpoint — its {@code base_entity_version_id} is — the association filter matches nothing. The fix also
 * joins on {@code csev.base_entity_version_id}.
 *
 * <p>Pinning to the supplement version is deliberate: it keeps the base version (which carries the association
 * directly on its own {@code csev.id}) out of scope so it cannot satisfy the filter and mask the defect.
 */
@MicronautTest(transactional = true)
class SupplementAssociationIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImport
  @Inject CodeSystemSupplementService supplementService
  @Inject CodeSystemEntityVersionService entityVersionService
  @Inject ConceptRepository conceptRepository

  static final String BASE_ID = "kl94-base"
  static final String BASE_URL = "http://termx-test.local/CodeSystem/kl94-base"
  static final String SUPP_ID = "kl94-supp"
  static final String SUPP_URL = "http://termx-test.local/CodeSystem/kl94-supp"
  static final String VERSION = "1.0.0"

  void setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "an is-a association on the base version resolves when querying the supplement entity version"() {
    given: "a base CS where child SA_CHILD is-a parent SA_PARENT"
    csImport.importCodeSystem(baseCodeSystem(), BASE_ID)
    def baseChildVersionId = versionId(BASE_ID, "SA_CHILD")

    and: "a supplement CS of that base"
    supplementService.supplement(BASE_ID, new CodeSystemSupplementRequest().setCodeSystem(SUPP_ID).setCodeSystemUri(SUPP_URL))

    and: "SA_CHILD supplemented into it — a new entity version linked to the base version via base_entity_version_id"
    def suppChildVersion = supplementService
        .supplement(SUPP_ID, VERSION, new CodeSystemSupplementRequest().setIds([baseChildVersionId]))
        .find { it.getCode() == "SA_CHILD" }

    expect: "the supplement version is genuine — it points at the base version, not itself"
    suppChildVersion != null
    suppChildVersion.getBaseEntityVersionId() == baseChildVersionId

    and: "control — pinned to the supplement version, the concept is queryable WITHOUT an association filter"
    queryCodes(pinnedTo(suppChildVersion.getId())) == ["SA_CHILD"] as Set

    when: "the same pinned query adds the is-a association filter"
    def codes = queryCodes(pinnedTo(suppChildVersion.getId()).setAssociationType("is-a"))

    then: "the association defined on the BASE version resolves through base_entity_version_id"
    codes == ["SA_CHILD"] as Set
  }

  private Long versionId(String codeSystem, String code) {
    entityVersionService.query(new CodeSystemEntityVersionQueryParams()
        .setCodeSystem(codeSystem)
        .setCode(code)
        .setPermittedCodeSystems([codeSystem])
        .tap { it.all() }).getData().find { it.getCode() == code }.getId()
  }

  private static ConceptQueryParams pinnedTo(Long suppVersionId) {
    new ConceptQueryParams()
        .setCodeSystem(BASE_ID)
        .setPermittedCodeSystems([BASE_ID, SUPP_ID])
        .setCodeSystemEntityVersionId(String.valueOf(suppVersionId))
  }

  private Set<String> queryCodes(ConceptQueryParams params) {
    params.all()
    return conceptRepository.query(params).getData().collect { it.getCode() } as Set
  }

  private static String baseCodeSystem() {
    FhirMapper.toJson([
        resourceType: "CodeSystem", id: BASE_ID, url: BASE_URL, name: "Kl94Base", title: "Kl94 Base",
        status: "active", content: "complete", version: VERSION, hierarchyMeaning: "is-a",
        concept: [[code: "SA_PARENT", display: "Parent",
                   concept: [[code: "SA_CHILD", display: "Child"]]]]])
  }
}
