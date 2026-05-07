package org.termx.terminology.terminology.mapset

import org.termx.core.auth.Authorized
import org.termx.terminology.Privilege
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Verifies that the three MapSet associations-export endpoints are gated by MS_TRIAGE
 * (per Phase A.2 of the GitHub-style permissions migration).
 *
 * Annotation-level test: the auth runtime is exercised by AuthorizationFilterTest;
 * here we just confirm the wire-up.
 */
class MapSetDownloadAuthAnnotationTest extends Specification {

  def "#methodName is gated by MS_TRIAGE"() {
    expect:
    findMethod(MapSetController, methodName).getAnnotation(Authorized).value().toList() ==
        [Privilege.MS_TRIAGE]

    where:
    methodName << ["exportAssociations", "getAssociationExportCSV", "getAssociationExportXLSX"]
  }

  def "MS_TRIAGE matches the expected dotted-string format"() {
    expect:
    Privilege.MS_TRIAGE == "MapSet.triage"
  }

  private static Method findMethod(Class<?> cls, String name) {
    cls.declaredMethods.find { it.name == name } ?:
        { throw new AssertionError("Method ${name} not found on ${cls.simpleName}") }()
  }
}
