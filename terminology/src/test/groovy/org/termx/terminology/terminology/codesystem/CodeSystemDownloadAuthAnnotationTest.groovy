package org.termx.terminology.terminology.codesystem

import org.termx.core.auth.Authorized
import org.termx.terminology.Privilege
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Verifies that the three CodeSystem export endpoints are gated by CS_TRIAGE
 * (per Phase A.2 of the GitHub-style permissions migration).
 *
 * Annotation-level test rather than HTTP-level: the auth runtime is exercised
 * by AuthorizationFilterTest; here we just confirm the wire-up.
 */
class CodeSystemDownloadAuthAnnotationTest extends Specification {

  def "exportConcepts is gated by CS_TRIAGE"() {
    expect:
    findMethod(CodeSystemController, "exportConcepts").getAnnotation(Authorized).value() ==
        [Privilege.CS_TRIAGE] as String[]
  }

  def "getConceptExportCSV is gated by CS_TRIAGE"() {
    expect:
    findMethod(CodeSystemController, "getConceptExportCSV").getAnnotation(Authorized).value() ==
        [Privilege.CS_TRIAGE] as String[]
  }

  def "getConceptExportXLSX is gated by CS_TRIAGE"() {
    expect:
    findMethod(CodeSystemController, "getConceptExportXLSX").getAnnotation(Authorized).value() ==
        [Privilege.CS_TRIAGE] as String[]
  }

  def "CS_TRIAGE matches the expected dotted-string format"() {
    expect:
    Privilege.CS_TRIAGE == "CodeSystem.triage"
  }

  private static Method findMethod(Class<?> cls, String name) {
    cls.declaredMethods.find { it.name == name } ?:
        { throw new AssertionError("Method ${name} not found on ${cls.simpleName}") }()
  }
}
