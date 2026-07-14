package org.termx.terminology.codesystem

import com.kodality.zmei.fhir.FhirMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.terminology.terminology.codesystem.entity.CodeSystemSupplementService
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams
import org.termx.ts.codesystem.CodeSystemSupplementRequest

import javax.sql.DataSource

/**
 * KL-109 — {@code cancel_code_system} must also delete rows attached DIRECTLY to the code system (via the
 * denormalized {@code code_system} column), not only rows reachable through {@code code_system_entity}.
 *
 * <p>This bites SUPPLEMENT code systems: a supplement's {@code code_system_entity_version} rows carry
 * {@code code_system = <supplement>}, but their {@code code_system_entity} still belongs to the BASE code
 * system (they link back via {@code base_entity_version_id}). Cancelling the SUPPLEMENT therefore never
 * reaches them through the {@code code_system_entity.code_system = p_code_system} subquery, so they are
 * orphaned and left {@code sys_status = 'A'}.
 *
 * <p>RED (before the fix): supplement entity versions remain active after cancel. GREEN (after adding
 * {@code or code_system = p_code_system} to the relevant update blocks): 0 active supplement rows remain.
 */
@MicronautTest(transactional = true)
class CancelSupplementCleanupIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImport
  @Inject CodeSystemSupplementService supplementService
  @Inject CodeSystemEntityVersionService entityVersionService
  @Inject CodeSystemService codeSystemService
  @Inject DataSource dataSource

  static final String BASE_ID = "kl109-base"
  static final String BASE_URL = "http://termx-test.local/CodeSystem/kl109-base"
  static final String SUPP_ID = "kl109-supp"
  static final String SUPP_URL = "http://termx-test.local/CodeSystem/kl109-supp"
  static final String VERSION = "1.0.0"
  static final String CODE = "KL109_A"

  void setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "cancelling a supplement deletes its directly-attached entity versions (KL-109)"() {
    given: "a BASE code system with one concept"
    csImport.importCodeSystem(baseCodeSystem(), BASE_ID)
    Long baseVersionId = versionId(BASE_ID, CODE)

    and: "a SUPPLEMENT of the base, with the base concept materialized as a supplement entity version"
    supplementService.supplement(BASE_ID,
        new CodeSystemSupplementRequest().setCodeSystem(SUPP_ID).setCodeSystemUri(SUPP_URL))
    supplementService.supplement(SUPP_ID, VERSION,
        new CodeSystemSupplementRequest().setIds([baseVersionId]))

    expect: "fixture control — the supplement owns >= 1 ACTIVE entity version whose code_system = supplement"
    activeSupplementEntityVersions() >= 1

    when: "the supplement is cancelled"
    codeSystemService.cancel(SUPP_ID)

    then: "no active entity version remains attached directly to the supplement"
    activeSupplementEntityVersions() == 0
  }

  private int activeSupplementEntityVersions() {
    def conn = dataSource.getConnection()
    try {
      def ps = conn.prepareStatement(
          "select count(*) as c from terminology.code_system_entity_version where code_system = ? and sys_status = 'A'")
      ps.setString(1, SUPP_ID)
      def rs = ps.executeQuery()
      rs.next()
      return rs.getInt("c")
    } finally {
      conn.close()
    }
  }

  private Long versionId(String codeSystem, String code) {
    def params = new CodeSystemEntityVersionQueryParams().setCodeSystem(codeSystem).setCode(code)
    params.all()
    return entityVersionService.query(params).getData().first().getId()
  }

  private static String baseCodeSystem() {
    FhirMapper.toJson([
        resourceType: "CodeSystem", id: BASE_ID, url: BASE_URL, name: "Kl109Base", title: "Kl109 Base",
        status: "active", content: "complete", version: VERSION,
        concept: [[code: CODE, display: "Alpha"]]])
  }
}
