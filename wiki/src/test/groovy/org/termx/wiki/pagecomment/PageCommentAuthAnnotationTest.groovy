package org.termx.wiki.pagecomment

import org.termx.core.auth.Authorized
import org.termx.wiki.Privilege
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Verifies the comment endpoints are gated correctly per Phase A.2:
 *  - query (GET) and create (POST) require W_TRIAGE  (positional @Authorized(value=...))
 *  - update / delete / resolve still require W_EDIT  (named @Authorized(privilege=...))
 *
 * Per Q4 of the migration spec, comment read is gated by triage (not view).
 *
 * The two annotation styles are tested explicitly:
 *  - positional: value() populated, privilege() empty
 *  - named:      value() empty,     privilege() populated
 */
class PageCommentAuthAnnotationTest extends Specification {

  def "#methodName uses positional @Authorized(W_TRIAGE)"() {
    given:
    def annotation = findMethod(PageCommentController, methodName).getAnnotation(Authorized)

    expect:
    annotation.value().toList() == [Privilege.W_TRIAGE]
    annotation.privilege() == ""

    where:
    methodName << ["query", "create"]
  }

  def "#methodName uses named @Authorized(privilege=W_EDIT)"() {
    given:
    def annotation = findMethod(PageCommentController, methodName).getAnnotation(Authorized)

    expect:
    annotation.privilege() == Privilege.W_EDIT
    annotation.value().toList() == []

    where:
    methodName << ["update", "delete", "resolve"]
  }

  def "W_TRIAGE matches the expected dotted-string format"() {
    expect:
    Privilege.W_TRIAGE == "Wiki.triage"
  }

  private static Method findMethod(Class<?> cls, String name) {
    cls.declaredMethods.find { it.name == name } ?:
        { throw new AssertionError("Method ${name} not found on ${cls.simpleName}") }()
  }
}
