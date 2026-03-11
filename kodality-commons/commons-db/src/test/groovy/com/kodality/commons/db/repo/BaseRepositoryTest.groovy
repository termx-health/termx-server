package com.kodality.commons.db.repo


import com.kodality.commons.model.QueryParams
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
    new QueryParams(sort: [])               | [:]                          | ""
    new QueryParams(sort: ["abc"])          | ["abc": "abc"]               | "order by abc asc"
    new QueryParams(sort: ["-abc"])         | ["abc": "abc"]               | "order by abc desc"
    new QueryParams(sort: ["-abc", "-xyz"]) | ["abc": "abc", "xyz": "xyz"] | "order by abc desc, xyz desc"
    new QueryParams(sort: ["-abc", "xyz"])  | ["abc": "abc", "xyz": "xyz"] | "order by abc desc, xyz asc"
  }

  def "should construct order expression correctly by function"() {
    setup:
    def mappings = [
        "text"  : { l -> new com.kodality.commons.db.sql.SqlBuilder().append("text -> ?", l) } as Function<String, com.kodality.commons.db.sql.SqlBuilder>,
        "simple": { l -> new com.kodality.commons.db.sql.SqlBuilder("simplesql") } as Function<String, com.kodality.commons.db.sql.SqlBuilder>,
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

}
