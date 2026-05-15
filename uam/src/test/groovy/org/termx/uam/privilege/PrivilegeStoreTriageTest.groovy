package org.termx.uam.privilege

import com.kodality.commons.model.QueryResult
import jakarta.inject.Provider
import org.termx.auth.Privilege
import org.termx.auth.PrivilegeQueryParams
import org.termx.auth.PrivilegeResource
import org.termx.auth.PrivilegeResource.PrivilegeResourceActions
import spock.lang.Specification

class PrivilegeStoreTriageTest extends Specification {

  PrivilegeService privilegeService = Mock(PrivilegeService)
  Provider<PrivilegeService> provider = { privilegeService } as Provider
  PrivilegeStore store = new PrivilegeStore(provider)

  private static Privilege priv(String code, PrivilegeResource... resources) {
    new Privilege(code: code, resources: resources.toList())
  }

  private static PrivilegeResource res(String type, String id, PrivilegeResourceActions actions) {
    new PrivilegeResource(resourceType: type, resourceId: id, actions: actions)
  }

  def "calculate emits *.X.triage when actions.triage is true"() {
    given:
    def actions = new PrivilegeResourceActions(read: true, triage: true, write: false, maintain: false)
    def privilege = priv("triage-role-1", res("CodeSystem", "icd-10", actions))

    when:
    Set<String> result = store.getPrivileges("triage-role-1")

    then:
    1 * privilegeService.query(_ as PrivilegeQueryParams) >> new QueryResult<Privilege>([privilege])
    result == ["icd-10.CodeSystem.read", "icd-10.CodeSystem.triage"] as Set
  }

  def "calculate does not emit triage when actions.triage is false (legacy row)"() {
    given:
    def actions = new PrivilegeResourceActions(read: true, triage: false, write: false, maintain: false)
    def privilege = priv("legacy-read-only", res("CodeSystem", "icd-10", actions))

    when:
    Set<String> result = store.getPrivileges("legacy-read-only")

    then:
    1 * privilegeService.query(_ as PrivilegeQueryParams) >> new QueryResult<Privilege>([privilege])
    result == ["icd-10.CodeSystem.read"] as Set
    !result.any { it.endsWith(".triage") }
  }

  def "calculate emits all four actions when all flags are true"() {
    given:
    def actions = new PrivilegeResourceActions(read: true, triage: true, write: true, maintain: true)
    def privilege = priv("full-role", res("CodeSystem", "icd-10", actions))

    when:
    Set<String> result = store.getPrivileges("full-role")

    then:
    1 * privilegeService.query(_ as PrivilegeQueryParams) >> new QueryResult<Privilege>([privilege])
    result == [
      "icd-10.CodeSystem.read",
      "icd-10.CodeSystem.triage",
      "icd-10.CodeSystem.write",
      "icd-10.CodeSystem.maintain"
    ] as Set
  }

  def "Admin resource type expands to *.*.* regardless of triage flag (#description)"() {
    given:
    def privilege = priv("admin-role-${description}", res("Admin", null, actions))

    when:
    Set<String> result = store.getPrivileges("admin-role-${description}")

    then:
    1 * privilegeService.query(_ as PrivilegeQueryParams) >> new QueryResult<Privilege>([privilege])
    result == ["*.*.*"] as Set

    where:
    description       | actions
    "triage-false"    | new PrivilegeResourceActions(triage: false)
    "triage-true"     | new PrivilegeResourceActions(triage: true)
    "all-flags-true"  | new PrivilegeResourceActions(read: true, triage: true, write: true, maintain: true)
  }

  def "Any resource type emits triage as *.<Type>.triage"() {
    given:
    def actions = new PrivilegeResourceActions(triage: true)
    def privilege = priv("any-triage", res("Any", null, actions))

    when:
    Set<String> result = store.getPrivileges("any-triage")

    then:
    1 * privilegeService.query(_ as PrivilegeQueryParams) >> new QueryResult<Privilege>([privilege])
    result == ["*.*.triage"] as Set
  }
}
