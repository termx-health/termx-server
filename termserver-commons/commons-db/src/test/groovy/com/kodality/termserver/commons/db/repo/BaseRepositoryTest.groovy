package com.kodality.termserver.commons.db.repo

import com.kodality.termserver.commons.db.sql.SqlBuilder
import com.kodality.termserver.commons.model.model.QueryParams
import spock.lang.Specification

import java.util.function.Function

class BaseRepositoryTest extends Specification {
  BaseRepository pgRepository = new BaseRepository()

  def "should construct order expression correctly"() {
    when:
    def result = pgRepository.order(queryParams, mapping)
    then:
    result.getSql() == expected
    where:
    queryParams                             | mapping                      | expected
    new QueryParams(sort: []) | [:] | ""
    new QueryParams(sort: ["abc"])          | ["abc": "abc"]               | "order by abc asc"
    new QueryParams(sort: ["-abc"])         | ["abc": "abc"]               | "order by abc desc"
    new QueryParams(sort: ["-abc", "-xyz"]) | ["abc": "abc", "xyz": "xyz"] | "order by abc desc, xyz desc"
    new QueryParams(sort: ["-abc", "xyz"])  | ["abc": "abc", "xyz": "xyz"] | "order by abc desc, xyz asc"
  }

  def "should construct order expression correctly by function"() {
    setup:
    def mappings = [
        "text"  : { l -> new SqlBuilder().append("text -> ?", l) } as Function<String, SqlBuilder>,
        "simple": { l -> new SqlBuilder("simplesql") } as Function<String, SqlBuilder>,
    ]
    when:
    def result = pgRepository.order(queryParams, mappings)
    then:
    result.getSql() == expectedSql
    result.getParams() == expectedParams
    where:
    queryParams                                  | expectedSql                             | expectedParams
    new QueryParams(sort: ["simple"])            | "order by simplesql asc"                | []
    new QueryParams(sort: ["text|en"])           | "order by text -> ? asc"                | ["en"]
    new QueryParams(sort: ["-text|en"])          | "order by text -> ? desc"               | ["en"]
    new QueryParams(sort: ["text|en", "simple"]) | "order by text -> ? asc, simplesql asc" | ["en"]
  }

  def "ordering should fail if parameter is not mapped"() {
    when:
    pgRepository.order(new QueryParams(sort: ["abc"]), [:])
    then:
    thrown(IllegalArgumentException)
  }

  def "should return empty sql builder if limit is negative"() {
    expect:
    pgRepository.limit(new QueryParams(limit: -1)).isEmpty()
  }

  def "should return limit with default 0 offset"() {
    when:
    def sqlBuilder = pgRepository.limit(new QueryParams(limit: 15))
    then:
    sqlBuilder.getSql() == "offset ? rows fetch first (?) rows only"
    sqlBuilder.paramsAsList == [0, 15]
  }

  def "should return limit with provided offset"() {
    when:
    def sqlBuilder = pgRepository.limit(new QueryParams(limit: 15, offset: 33))
    then:
    sqlBuilder.getSql() == "offset ? rows fetch first (?) rows only"
    sqlBuilder.paramsAsList == [33, 15]
  }

  def "prefix generator test"(String[] fields, String expected) {
    when:
    def result = pgRepository.prefix("alias", fields)
    then:
    result == expected
    where:
    fields               | expected
    []                   | ""
    ["field1"]           | "alias.field1 alias_field1"
    ["field1", "field2"] | "alias.field1 alias_field1, alias.field2 alias_field2"
  }
}
