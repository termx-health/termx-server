package com.kodality.commons.db.sql

import spock.lang.Specification

import java.util.function.Supplier

class SaveSqlBuilderTest extends Specification {

  def "empty properties should throw exception"() {
    setup:
    def ssb = new SaveSqlBuilder();
    when:
    def sb = ssb.buildInsert("table", "id")
    then:
    thrown IllegalStateException
  }

  def "only id property should throw exception"() {
    setup:
    def ssb = new SaveSqlBuilder();
    when:
    ssb.property("id", 1);
    def sb = ssb.buildInsert("table", "id")
    then:
    thrown IllegalStateException
  }

  def "empty id should insert"() {
    setup:
    def ssb = new SaveSqlBuilder();
    when:
    ssb.property("somefield", 1);
    def sb = ssb.buildSave("table", "id")
    then:
    sb.getSql().startsWith("insert")

    when:
    ssb.property("id", null)
    ssb.property("somefield", 1);
    sb = ssb.buildSave("table", "id")
    then:
    sb.getSql().startsWith("insert")
  }

  def "existing id should update"() {
    setup:
    def ssb = new SaveSqlBuilder();
    when:
    ssb.property("id", 1);
    ssb.property("somefield", 2);
    def sb = ssb.buildSave("table", "id")
    then:
    sb.getSql().startsWith("update")
  }
  
  def "update shoult check additional fields"() {
    setup:
    def ssb = new SaveSqlBuilder();
    when:
    ssb.property("id", 1);
    ssb.property("somefield", 2);
    ssb.property("tenant", "aaaa");
    def sb = ssb.buildSave("table", "id")
    then:
    sb.getSql() == "update table set somefield = ?, tenant = ? where id = ? returning id"
    sb.getParams() == [2, "aaaa", 1]

    when:
    sb = ssb.buildSave("table", "id", "tenant")
    then:
    sb.getSql() == "update table set somefield = ?, tenant = ? where id = ? and tenant = ? returning id"
    sb.getParams() == [2, "aaaa", 1, "aaaa"]
  }

  enum E { A, B, C }
  def "multiple properties"() {
    setup:
    def ssb = new SaveSqlBuilder();
    ssb.property("id", 1);
    ssb.property("f1", 2);
    ssb.property("f2", "3");
    ssb.property("f3", "?::datetime", "123");
    ssb.property("f4", "?::json", Collections.singletonMap("a", "b"));
    ssb.jsonProperty("f5", Collections.singletonMap("c", "d"), false);
    ssb.property("f6",{_ -> "lambda"} as Supplier<String>);
    ssb.enumProperty("f7",  E.A)

    when:
    def sb = ssb.buildInsert("table", "id")
    then:
    sb.getSql() == "insert into table ( f1,f2,f3,f4,f5,f6,f7 ) select ?, ?, ?::datetime, ?::json, ?::jsonb, ?, ? returning id"
    sb.getParams() == [
      2,
      "3",
      "123",
      ["a":"b"],
      "{\"c\":\"d\"}",
      "lambda",
      'A'
    ]

    when:
    sb = ssb.buildUpdate("table", "id")
    then:
    sb.getSql() == "update table set f1 = ?, f2 = ?, f3 = ?::datetime, f4 = ?::json, f5 = ?::jsonb, f6 = ?, f7 = ? where id = ? returning id"
    sb.getParams() == [
      2,
      "3",
      "123",
      ["a":"b"],
      "{\"c\":\"d\"}",
      "lambda",
      'A',
      1
    ]
  }
}
