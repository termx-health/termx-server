package com.kodality.termserver.commons.client


import spock.lang.Specification

class UrlUtilTest extends Specification {

  def "build url "(String[] input, String expected) {
    expect:
    UrlUtil.url(input) == expected

    where:
    input                     | expected
    null                      | ""
    []                        | ""
    ["http://abc"]            | "http://abc"
    ["http://abc/"]           | "http://abc"
    ["http://abc", null]      | "http://abc"
    ["http://abc", "path"]    | "http://abc/path"
    ["http://abc/", "path"]   | "http://abc/path"
    ["http://abc/", "/path/"] | "http://abc/path"
  }

}
