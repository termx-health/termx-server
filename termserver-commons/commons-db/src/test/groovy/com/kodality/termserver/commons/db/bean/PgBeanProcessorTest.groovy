package com.kodality.termserver.commons.db.bean

import com.fasterxml.jackson.annotation.JsonValue
import com.kodality.termserver.commons.model.model.CodeName
import spock.lang.Specification

import java.sql.ResultSet
import java.sql.ResultSetMetaData

class PgBeanProcessorTest extends Specification {
  enum E1 { A, B }
  enum E2 {
    A('ma'), B('mb');
    private json;
    E2(json) { this.json = json }
    @JsonValue String getJson() { json }
  }

  def 'should read enums'() {
    when:
    def enumProcessor = PgBeanProcessor.toEnum()
    def results = makeResultSet(['enum_column'], [value])

    then:
    expectedResult == enumProcessor.processColumn(results, 0, type)

    where:
    type | value | expectedResult
    E1   | 'A'   | E1.A
    E1   | 'B'   | E1.B
    E2   | 'ma'  | E2.A
    E2   | 'mb'  | E2.B
  }

  def "CodeName column processor works correctly"() {
    setup:
    def bp = new PgBeanProcessor(TestBean.class)
    def dbColumn = "test_id"
    def beanProperty = "test"
    def aColumns = ["id", dbColumn]
    def expectedId = 5L
    def result = makeResultSet(
        aColumns,
        [1, expectedId]
    )
    bp.addColumnProcessor(dbColumn, beanProperty, PgBeanProcessor.toIdCodeName());
    when:
    def cn = bp.processColumn(result, aColumns.indexOf(dbColumn), CodeName.class)
    then:
    cn instanceof CodeName
    cn.id == expectedId
    when:
    result = makeResultSet(
        aColumns,
        [2, null]
    )
    cn = bp.processColumn(result, aColumns.indexOf(dbColumn), CodeName.class)
    then:
    cn == null
  }

  def makeResultSet(List<String> aColumns, List... rows) {
    ResultSet result = Mock()
    int currentIndex = -1
    result.next() >> { ++currentIndex < rows.length }
    result./get(String|Short|Date|Int|Timestamp|Object)/(_) >> { int argument ->
      rows[currentIndex][aColumns.indexOf(argument)]
    }
    result.getMetaData() >> {
      def metadata = Mock(ResultSetMetaData)
      metadata.getColumnName(_) >> { int idx -> aColumns.get(idx) }
      metadata
    }

    return result
  }
}
