package com.kodality.termserver.commons.db.sql

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
    sb.getSql().startsWith("INSERT")

    when:
    ssb.property("id", null)
    ssb.property("somefield", 1);
    sb = ssb.buildSave("table", "id")
    then:
    sb.getSql().startsWith("INSERT")
  }

  def "existing id should update"() {
    setup:
    def ssb = new SaveSqlBuilder();
    when:
    ssb.property("id", 1);
    ssb.property("somefield", 2);
    def sb = ssb.buildSave("table", "id")
    then:
    sb.getSql().startsWith("UPDATE")
  }

  def "update should check additional fields"() {
    setup:
    def ssb = new SaveSqlBuilder();
    when:
    ssb.property("id", 1);
    ssb.property("somefield", 2);
    ssb.property("tenant", "aaaa");
    def sb = ssb.buildSave("table", "id")
    then:
    sb.getSql() == "UPDATE table SET somefield = ?, tenant = ? WHERE id = ? RETURNING id"
    sb.getParams() == [2, "aaaa", 1]

    when:
    sb = ssb.buildSave("table", "id", "tenant")
    then:
    sb.getSql() == "UPDATE table SET somefield = ?, tenant = ? WHERE id = ? AND tenant = ? RETURNING id"
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
    ssb.jsonProperty("f5", Collections.singletonMap("c", "d"));
    ssb.property("f6",{_ -> "lambda"} as Supplier<String>);
    ssb.enumProperty("f7",  E.A)

    when:
    def sb = ssb.buildInsert("table", "id")
    then:
    sb.getSql() == "INSERT INTO table ( f1,f2,f3,f4,f5,f6,f7 ) SELECT ?, ?, ?::datetime, ?::json, ?::jsonb, ?, ? RETURNING id"
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
    sb.getSql() == "UPDATE table SET f1 = ?, f2 = ?, f3 = ?::datetime, f4 = ?::json, f5 = ?::jsonb, f6 = ?, f7 = ? WHERE id = ? RETURNING id"
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

  def "insert without id field should be supported"() {
    setup:
    def ssb = new SaveSqlBuilder();
    when:
    ssb.property("somefield", 1);
    def sb = ssb.buildInsert("table")
    then:
    sb.getSql() == "INSERT INTO table ( somefield ) SELECT ?"
  }
}
