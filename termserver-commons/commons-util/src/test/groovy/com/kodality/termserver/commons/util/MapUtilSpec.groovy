package com.kodality.termserver.commons.util

import spock.lang.Specification

class MapUtilSpec extends Specification {
  def "varargs to map"() {
    expect:
    MapUtil.toMap() == [:]
    MapUtil.toMap('a', null) == [:]
    MapUtil.toMap('a', 1) == ['a': 1]
    MapUtil.toMap('a', 1, 'b', null) == ['a': 1]
    MapUtil.toMap('a', 1, 'b', 2) == ['a': 1, 'b': 2]
    MapUtil.toMap('a', 1, 'b', 2, 'c', 3) == ['a': 1, 'b': 2, 'c': 3]
  }
}
