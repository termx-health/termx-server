package com.kodality.termserver.auth.auth

import spock.lang.Specification


class AuthorizationFilterTest extends Specification {

  def "privilege check works"() {
    expect:
    AuthorizationFilter.hasAnyPrivilege(authPrivs, userPrivs) == result
    where:
    authPrivs                              | userPrivs                              | result
    ['*.view']                             | ['icd-10.CodeSystem.view']             | false
    ['*.*.view']                           | ['icd-10.CodeSystem.view']             | false
    ['*.CodeSystem.view']                  | ['icd-10.CodeSystem.view']             | false
    ['admin']                              | ['*.*.edit']                           | false
    ['icd-10.CodeSystem.view', 'a.b.edit'] | ['icd-10.CodeSystem.view']             | true
    ['icd-10.CodeSystem.view']             | ['icd-10.CodeSystem.view', 'a.b.edit'] | true
    ['*.CodeSystem.edit']                  | ['*.*.edit']                           | true
    ['ABS.CodeSystem.edit']                | ['*.edit']                             | true
    ['*.*.view']                           | ['admin']                              | true
  }
}
