package org.termx.terminology.terminology.valueset

import org.termx.core.auth.Authorized
import org.termx.terminology.Privilege
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Verifies that the three ValueSet expansion-export endpoints are gated by VS_TRIAGE
 * (per Phase A.2 of the GitHub-style permissions migration).
 *
 * Annotation-level test: the auth runtime is exercised by AuthorizationFilterTest;
 * here we just confirm the wire-up.
 */
class ValueSetDownloadAuthAnnotationTest extends Specification {

  def "#methodName is gated by VS_TRIAGE"() {
    expect:
    findMethod(ValueSetController, methodName).getAnnotation(Authorized).value().toList() ==
        [Privilege.VS_TRIAGE]

    where:
    methodName << ["exportConcepts", "getConceptExportCSV", "getConceptExportXLSX"]
  }

  def "VS_TRIAGE matches the expected dotted-string format"() {
    expect:
    Privilege.VS_TRIAGE == "ValueSet.triage"
  }

  private static Method findMethod(Class<?> cls, String name) {
    cls.declaredMethods.find { it.name == name } ?:
        { throw new AssertionError("Method ${name} not found on ${cls.simpleName}") }()
  }
}
