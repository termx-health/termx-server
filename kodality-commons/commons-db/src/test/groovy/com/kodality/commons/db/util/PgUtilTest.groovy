package com.kodality.commons.db.util


import spock.lang.Specification

class PgUtilTest extends Specification {

  def "from pg array"(list, expect) {
    setup:
    def result = PgUtil.array(list)
    expect:
    result == expect
    where:
    list       | expect
    null       | null
    []         | "{}"
    ["1"]      | "{1}"
    ["1", "2"] | "{1,2}"
  }

//  def "to pg array"(pgArr, expect) {
//    setup:
//    def result = PgUtil.array(pgArr)
//    result = result == null ? null : new ArrayList(result)
//    expect:
//    result == expect
//    where:
//    pgArr   | expect
//    null    | null
//    "{}"    | []
//    "{1}"   | ["1"]
//    "{1,2}" | ["1", "2"]
//  }


}
