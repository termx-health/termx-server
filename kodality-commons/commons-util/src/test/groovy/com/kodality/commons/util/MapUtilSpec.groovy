package com.kodality.commons.util

import spock.lang.Specification

class MapUtilSpec extends Specification {

  def "varargs to map"() {
    expect:
    MapUtil.toMap() == [:]
    MapUtil.toMap("a", null) == ["a": null]
    MapUtil.toMap("a", 1) == ["a": 1]
    MapUtil.toMap("a", 1, "b", 2) == ["a": 1, "b": 2]
    MapUtil.toMap("a", 1, "b", 2, "c", 3) == ["a": 1, "b": 2, "c": 3]
  }

  def "array to map"() {
    expect:
    MapUtil.toMap([] as Object[][]) == [:]
    MapUtil.toMap([["a", 1]] as Object[][]) == ["a": 1]
    MapUtil.toMap([["a", 1], ["b", 2]] as Object[][]) == ["a": 1, "b": 2]
    MapUtil.toMap([["a", 1], ["b", 2], ["c", 3]] as Object[][]) == ["a": 1, "b": 2, "c": 3]
  }

  def "flatten"() {
    expect:
    MapUtil.flatten((Map) null) == null
    MapUtil.flatten([:]) == [:]
    MapUtil.flatten(["a": null]) == ["a": null]
    MapUtil.flatten(["a": 1]) == ["a": 1]
    MapUtil.flatten(["a": "b"]) == ["a": "b"]
    MapUtil.flatten(["a": ["b": "c", "d": "e"]]) == ["a.b": "c", "a.d": "e"]
    MapUtil.flatten(["a": ["b", "c"]]) == ["a.0": "b", "a.1": "c"]
  }
}
