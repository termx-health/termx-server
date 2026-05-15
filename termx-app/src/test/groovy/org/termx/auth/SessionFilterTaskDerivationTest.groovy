package org.termx.auth

import org.termx.core.auth.SessionInfo
import spock.lang.Specification

class SessionFilterTaskDerivationTest extends Specification {

  SessionFilter filter = new SessionFilter([])

  def "triage on CodeSystem derives Task.read"() {
    given:
    def session = new SessionInfo(privileges: ["icd-10.CodeSystem.triage"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    session.privileges.contains("code-system#icd-10.Task.read")
    !session.privileges.contains("code-system#icd-10.Task.write")
    !session.privileges.contains("code-system#icd-10.Task.maintain")
  }

  def "write on CodeSystem derives Task.read + Task.write"() {
    given:
    def session = new SessionInfo(privileges: ["icd-10.CodeSystem.write"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    session.privileges.contains("code-system#icd-10.Task.read")
    session.privileges.contains("code-system#icd-10.Task.write")
    !session.privileges.contains("code-system#icd-10.Task.maintain")
  }

  def "maintain on CodeSystem derives all three Task privileges"() {
    given:
    def session = new SessionInfo(privileges: ["icd-10.CodeSystem.maintain"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    session.privileges.contains("code-system#icd-10.Task.read")
    session.privileges.contains("code-system#icd-10.Task.write")
    session.privileges.contains("code-system#icd-10.Task.maintain")
  }

  def "triage on ValueSet derives value-set Task.read"() {
    given:
    def session = new SessionInfo(privileges: ["my-vs.ValueSet.triage"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    session.privileges.contains("value-set#my-vs.Task.read")
  }

  def "read alone (no triage) does NOT derive Task privileges"() {
    given:
    def session = new SessionInfo(privileges: ["icd-10.CodeSystem.read"] as Set)

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
    def session = new SessionInfo(privileges: ["*.*.*", "icd-10.CodeSystem.write"] as Set)

    when:
    filter.deriveTaskPrivileges(session)

    then:
    session.privileges == ["*.*.*", "icd-10.CodeSystem.write"] as Set
    !session.privileges.any { it.contains("Task.") }
  }
}
