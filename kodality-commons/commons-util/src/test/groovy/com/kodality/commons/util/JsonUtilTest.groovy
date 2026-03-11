package com.kodality.commons.util

import spock.lang.Specification

class JsonUtilTest extends Specification {

  def "test pretty print"() {
    def json = JsonUtil.toPrettyJson(["one": ["two": 3]])

    expect:
    json == """{
  "one" : {
    "two" : 3
  }
}"""
  }

  def "deserialize empty string json as null"() {
    expect:
    JsonUtil.fromJson("", String.class) == null
  }

  
  def "for objects, JsonUtil.toMap gives the same result as JsonUtil.fromJson(_, Object.class)"() {
    def json = """{"id": 1, "name": "Jason" }"""
    
    expect:
    JsonUtil.toMap(json) == JsonUtil.fromJson(json, Object.class)
    JsonUtil.toMap(json) instanceof java.util.HashMap
    JsonUtil.fromJson(json, Object.class) instanceof java.util.HashMap
    JsonUtil.toMap(json).keySet().size() == 2
  }
  
  def "JsonUtil.fromJson(_, Object.class) also handles json arrays"() {
    def json = """[{"id": 1, "name": "Jason" }, {"id": 2, "name": "Xamel" }]"""
    
    expect:
    JsonUtil.fromJson(json, Object.class) instanceof java.util.List
    JsonUtil.fromJson(json, Object.class).size() == 2
  }
}
