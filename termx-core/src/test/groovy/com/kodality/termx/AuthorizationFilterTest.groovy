package com.kodality.termx

import com.kodality.termx.auth.SessionInfo
import spock.lang.Specification

class AuthorizationFilterTest extends Specification {
  def "privilege check works"() {
    expect:
    new SessionInfo(privileges: userPrivs).hasAnyPrivilege(authPrivs) == result
    where:
    authPrivs                              | userPrivs                              | result
    ['*.view']                             | ['icd-10.CodeSystem.view']             | true
    ['*.*.view']                           | ['icd-10.CodeSystem.view']             | true
    ['*.CodeSystem.view']                  | ['icd-10.CodeSystem.view']             | true
    ['icd-10.CodeSystem.view', 'a.b.edit'] | ['icd-10.CodeSystem.view']             | true
    ['icd-10.CodeSystem.view']             | ['icd-10.CodeSystem.view', 'a.b.edit'] | true
    ['*.CodeSystem.edit']                  | ['*.*.edit']                           | true
    ['ABS.CodeSystem.edit']                | ['*.edit']                             | true
    ['*.*.view']                           | ['admin']                              | true
    ['admin']                              | ['*.*.edit']                           | false
    ['*.CodeSystem.view']                  | ['*.CodeSystem.edit']                  | false
    ['ABS.CodeSystem.edit']                | ['SBA.CodeSystem.edit']                | false
  }

  def "privilege resource check works"() {
    expect:
    new SessionInfo(privileges: userPrivs).hasAnyPrivilege(authPrivs, Optional.of('icd-10')) == result
    where:
    authPrivs                              | userPrivs                              | result
    ['*.view']                             | ['icd-10.CodeSystem.view']             | true
    ['*.view']                             | ['*.CodeSystem.view']                  | true
    ['*.*.view']                           | ['icd-10.CodeSystem.view']             | true
    ['*.*.view']                           | ['*.CodeSystem.view']                  | true
    ['*.CodeSystem.view']                  | ['icd-10.CodeSystem.view']             | true
    ['*.CodeSystem.view']                  | ['*.CodeSystem.view']                  | true
    ['icd-10.CodeSystem.view', 'a.b.edit'] | ['icd-10.CodeSystem.view']             | true
    ['icd-10.CodeSystem.view']             | ['icd-10.CodeSystem.view', 'a.b.edit'] | true
    ['*.CodeSystem.edit']                  | ['*.*.edit']                           | true
    ['ABS.CodeSystem.edit']                | ['*.edit']                             | true
    ['*.*.view']                           | ['admin']                              | true
    ['icd-11.CodeSystem.edit']             | ['icd-10.CodeSystem.edit']             | false
    ['icd-10.CodeSystem.edit']             | ['icd-11.CodeSystem.edit']             | false
    ['*.edit']                             | ['icd-11.CodeSystem.edit']             | false
  }
}
