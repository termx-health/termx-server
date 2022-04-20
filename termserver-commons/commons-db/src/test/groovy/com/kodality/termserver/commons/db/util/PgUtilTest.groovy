package com.kodality.termserver.commons.db.util

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

}
