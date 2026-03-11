package com.kodality.commons.db.sql

import com.kodality.commons.util.DateUtil
import spock.lang.Specification

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime

class SqlBuilderTest extends Specification {

  def "getPretty"() {
    expect:
    new SqlBuilder("select 1").getPretty() == "select 1"
    new SqlBuilder("select %").getPretty() == "select %"
    new SqlBuilder("select %?", "a").getPretty() == "select %'a'"
    new SqlBuilder("select ?", "a").getPretty() == "select 'a'"
    new SqlBuilder("select ?", 1L).getPretty() == "select 1"
    new SqlBuilder("select ? ?", "a", 1L).getPretty() == "select 'a' 1"
    Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse("2010-01-02T03:04:05");
    new SqlBuilder("select ?", d).getPretty() == "select '2010-01-02 03:04:05'"
    LocalDate ld = DateUtil.parseDate("2010-01-02");
    new SqlBuilder("select ?", ld).getPretty() == "select to_date('2010-01-02', 'yyyy-MM-dd')"
    LocalDateTime ldt = DateUtil.parseDateTime("2010-01-02T03:04:05");
    new SqlBuilder("select ?", ldt).getPretty() == "select '2010-01-02 03:04:05'"
  }
  
  def "add params should do nothing when no parameters given"() {
    setup:
    def sqlBuilder = new SqlBuilder()
    when:
    sqlBuilder.add()
    sqlBuilder.add(null)
    then:
    sqlBuilder.getParams().size() == 0
  }

  def "append should add sql and params"() {
    setup:
    def sqlBuilder = new SqlBuilder()
    when:
    sqlBuilder.append("select * from t where a = ? and b = ?", "foo", 12)
    then:
    sqlBuilder.getParamsAsList() == ["foo", 12]
    sqlBuilder.getSql() == "select * from t where a = ? and b = ?"
  }

  def "appending other SqlBuilder should append sql and params"() {
    setup:
    def sbA = new SqlBuilder("select * from t where a = ?", "foo")
    def sbB = new SqlBuilder("and b = ?", 12)
    when:
    sbA.append(sbB)
    then:
    sbA.getParamsAsList() == ["foo", 12]
    sbA.getSql() == "select * from t where a = ? and b = ?"
  }

  def "appendIfTrue() should consider condition value"() {
    setup:
    def sqlBuilder = new SqlBuilder("select * from t where 1=1")
    when:
    sqlBuilder.appendIfTrue(true, "and a = ?", "foo")
    sqlBuilder.appendIfTrue(false, "and b = ?", 12)
    then:
    sqlBuilder.getParamsAsList() == ["foo"]
    sqlBuilder.getSql() == "select * from t where 1=1 and a = ?"
  }

  def "appendIfNotNull() should consider parameter value"() {
    setup:
    def sqlBuilder = new SqlBuilder("select * from t where 1=1")
    when:
    sqlBuilder.appendIfNotNull("and a = ?", null)
    sqlBuilder.appendIfNotNull("and b = ?", 12)
    then:
    sqlBuilder.getParamsAsList() == [12]
    sqlBuilder.getSql() == "select * from t where 1=1 and b = ?"
  }

  def "and() should add empty 'AND' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where 1=1").and()
    then:
    sqlBuilder.sql == "select * from t where 1=1 and"
  }

  def "not() should add empty 'not' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where 1=1").not()
    then:
    sqlBuilder.sql == "select * from t where 1=1 not"
  }

  def "and(sql, params) should add 'AND' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where 1=1").and("a = ?", "foo")
    then:
    sqlBuilder.sql == "select * from t where 1=1 and a = ?"
    sqlBuilder.getParamsAsList() == ["foo"]
  }

  def "and(sqBuilder) should add 'AND' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where 1=1").and(new SqlBuilder("b = ?", 12))
    then:
    sqlBuilder.sql == "select * from t where 1=1 and b = ?"
    sqlBuilder.getParamsAsList() == [12]
  }

  def "or() should add empty 'OR' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where 1=1").or()
    then:
    sqlBuilder.sql == "select * from t where 1=1 or"
  }

  def "or(sql, params) should add 'OR' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where 1=1").or("a = ?", "foo")
    then:
    sqlBuilder.sql == "select * from t where 1=1 or a = ?"
    sqlBuilder.getParamsAsList() == ["foo"]
  }

  def "or(sqBuilder) should add 'OR' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where 1=1").or(new SqlBuilder("b = ?", 12))
    then:
    sqlBuilder.sql == "select * from t where 1=1 or b = ?"
    sqlBuilder.getParamsAsList() == [12]
  }

  def "eq() should add '=' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where").eq("a", 12)
    then:
    sqlBuilder.sql == "select * from t where a = ?"
    sqlBuilder.getParamsAsList() == [12]
  }

  def "eq() should add 'IS NULL' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where").eq("a", null)
    then:
    sqlBuilder.sql == "select * from t where a is null"
    sqlBuilder.getParamsAsList().empty
  }

  def "ne() should add '!=' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where").ne("a", 12)
    then:
    sqlBuilder.sql == "select * from t where a != ?"
    sqlBuilder.getParamsAsList() == [12]
  }

  def "eq() should add 'IS NOT NULL' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where").ne("a", null)
    then:
    sqlBuilder.sql == "select * from t where a is not null"
    sqlBuilder.getParamsAsList().empty
  }

  def "in() should accept all when param is null"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where")
    sqlBuilder = sqlBuilder.in("a", null)
    then:
    sqlBuilder.sql == "select * from t where true"
    sqlBuilder.getParamsAsList().empty
  }

  def "in() should reject all when param is not null, empty"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where")
    sqlBuilder = sqlBuilder.in("b", [])
    then:
    sqlBuilder.sql == "select * from t where false"
    sqlBuilder.getParamsAsList().empty
  }

  def "in() should use '=' operator in case of a single parameter"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where").in("a", 12)
    then:
    sqlBuilder.sql == "select * from t where a = ?"
    sqlBuilder.getParamsAsList() == [12]
  }

  def "in() should use 'IN' operator when parameter count > 1"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where").in("a", [18, 12, 14, 16])
    then:
    sqlBuilder.sql == "select * from t where a in (?,?,?,?)"
    sqlBuilder.getParamsAsList() == [18, 12, 14, 16]
  }

  def "notIn() should accept all when param is null or empty"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where")
    sqlBuilder = sqlBuilder.notIn("a", null)
    sqlBuilder = sqlBuilder.append('and').notIn("b", [])
    then:
    sqlBuilder.sql == "select * from t where true and true"
    sqlBuilder.getParamsAsList().empty
  }

  def "notIn() should use '!=' operator in case of a single parameter"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where").notIn("a", 12)
    then:
    sqlBuilder.sql == "select * from t where a != ?"
    sqlBuilder.getParamsAsList() == [12]
  }

  def "notIn() should use 'NOT IN' operator when parameter count > 1"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where").notIn("a", [18, 12, 14, 16])
    then:
    sqlBuilder.sql == "select * from t where a not in (?,?,?,?)"
    sqlBuilder.getParamsAsList() == [18, 12, 14, 16]
  }

  def "orderBy() should add 'ORDER BY' clause"() {
    when:
    def sqlBuilder = new SqlBuilder("select * from t where 1=1").orderBy("id desc")
    then:
    sqlBuilder.sql == "select * from t where 1=1 order by id desc"
    sqlBuilder.getParamsAsList().empty
  }

  def "isEmpty() should return correct values"() {
    expect:
    new SqlBuilder().isEmpty()
    !new SqlBuilder("select 1 from dual").isEmpty()
  }
  
  def "pipe(empty) should return empty sql"() {
    when:
    def result = new SqlBuilder().pipe("keyy", "valuee", "");
    then:
    result.sql == "";
    result.params == [];
  }
  
  def "pipe(not piped) should return only value clause"() {
    when:
    def result = new SqlBuilder().pipe("keyy", "valuee", "bbb");
    then:
    result.sql == "valuee = ?";
    result.params == ["bbb"];
  }

  def "pipe(piped) should return key and value clauses"() {
    when:
    def result = new SqlBuilder().pipe("keyy", "valuee", "aaa|bbb");
    then:
    result.sql == "( keyy = ? and valuee = ? )";
    result.params == ["aaa", "bbb"];
  }


}
