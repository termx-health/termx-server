package org.termx.auth

import org.termx.core.auth.SessionInfo
import spock.lang.Specification

class SessionFilterTaskDerivationTest extends Specification {

  SessionFilter filter = new SessionFilter([])

  def "triage on CodeSystem derives Task.view"() {
    given:
    def session = new SessionInfo(privileges: ["icd-10.CodeSystem.triage"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    session.privileges.contains("code-system#icd-10.Task.view")
    !session.privileges.contains("code-system#icd-10.Task.edit")
    !session.privileges.contains("code-system#icd-10.Task.publish")
  }

  def "edit on CodeSystem still derives Task.view + Task.edit (Phase A vocabulary unchanged)"() {
    given:
    def session = new SessionInfo(privileges: ["icd-10.CodeSystem.edit"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    session.privileges.contains("code-system#icd-10.Task.view")
    session.privileges.contains("code-system#icd-10.Task.edit")
    !session.privileges.contains("code-system#icd-10.Task.publish")
  }

  def "publish on CodeSystem still derives all three Task privileges"() {
    given:
    def session = new SessionInfo(privileges: ["icd-10.CodeSystem.publish"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    session.privileges.contains("code-system#icd-10.Task.view")
    session.privileges.contains("code-system#icd-10.Task.edit")
    session.privileges.contains("code-system#icd-10.Task.publish")
  }

  def "triage on ValueSet derives value-set Task.view"() {
    given:
    def session = new SessionInfo(privileges: ["my-vs.ValueSet.triage"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    session.privileges.contains("value-set#my-vs.Task.view")
  }

  def "view alone (no triage) does NOT derive Task privileges"() {
    given:
    def session = new SessionInfo(privileges: ["icd-10.CodeSystem.view"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    !session.privileges.any { it.startsWith("code-system#") }
  }

  def "Admin (*.*.*) short-circuits derivation"() {
    given:
    def session = new SessionInfo(privileges: ["*.*.*"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    session.privileges == ["*.*.*"] as Set
    !session.privileges.any { it.contains("Task.") }
  }

  def "Admin combined with another privilege still short-circuits derivation"() {
    given:
    def session = new SessionInfo(privileges: ["*.*.*", "icd-10.CodeSystem.edit"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    session.privileges == ["*.*.*", "icd-10.CodeSystem.edit"] as Set
    !session.privileges.any { it.contains("Task.") }
  }
}
