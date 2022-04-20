package com.kodality.termserver.commons.util

import com.fasterxml.jackson.core.type.TypeReference
import com.kodality.termserver.commons.util.JsonUtil
import spock.lang.Specification

import java.time.OffsetDateTime

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

  def "should fail on incorrect json"() {
    when:
    JsonUtil.fromJson("{bad-syntax: 123}", TestObject)
    then:
    thrown(RuntimeException)
  }

  def "deserialize empty string json as null"() {
    expect:
    JsonUtil.fromJson("", String.class) == null
  }

  def "deserialize from java type"() {
    List<TestObject> list = JsonUtil.fromJson("[{\"testInt\":1},{\"testInt\":2}]", JsonUtil.getListType(TestObject.class))
    expect:
    list.size() == 2
    list[0].class == TestObject
    list[0].testInt == 1
    list[1].testInt == 2
  }

  def "deserialize from type reference"() {
    List<TestObject> list = JsonUtil.fromJson("[{\"testInt\":1},{\"testInt\":2}]", new TypeReference<List<TestObject>>() {
    })
    expect:
    list.size() == 2
    list[0].class == TestObject
    list[0].testInt == 1
    list[1].testInt == 2
  }

  def "deserialize parametrized"() {
    ParametrizedObject<Integer> pmt = JsonUtil.fromJson("{\"data\":1}", JsonUtil.getParametricType(ParametrizedObject, Integer))
    expect:
    pmt.data == 1
  }

  def "deserialize to map"() {
    Map<String, String> map = JsonUtil.toMap("{\"key\":\"123\"}")
    expect:
    map["key"] == "123"
  }


  def "check consistency when serializing and deserializing the same object"() {
    def object = new TestObject(testInt: 1, testString: "some string", testMap: Map.of("some-key", "some-value"))
    when:
    def json = JsonUtil.toJson(object)
    def deserialized = JsonUtil.fromJson(json, TestObject)
    then:
    deserialized.testInt == 1
    deserialized.testString == "some string"
    deserialized.testMap['some-key'] == "some-value"
  }

  def "should serialize & deserialize offset datetime"() {
    def date = OffsetDateTime.parse('2022-03-30T10:36:30.140141+03:00')
    when:
    def dateJson = JsonUtil.toJson(date)
    def parsedDate = JsonUtil.fromJson(dateJson, OffsetDateTime.class)
    then:
    dateJson == '"2022-03-30T10:36:30.140141+03:00"'
    parsedDate.isEqual(date)
  }
  def "should serialize to empty object"() {
    when:
    def json = JsonUtil.toJson(new TestObject(testInt: 10, testString: 'foobar', testMap: new HashMap<String, String>()))
    then:
    json == '{"testInt":10,"testString":"foobar","testMap":{}}'
  }

  static class TestObject {
    int testInt
    String testString
    Map<String, String> testMap
  }

  static class ParametrizedObject<T> {
    T data
  }

}
