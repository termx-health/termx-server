package com.kodality.commons.db.resultset

import spock.lang.Specification

import java.sql.ResultSet

class ResultSetUtilSpec extends Specification {

  def result = makeResultSet(
      ["id", "name"],
      [  1 , "Hamlet"],
      [  2 , "Andres"],
      [  3 , "Dierk"]
  )

  def "getLong returns expected #longs with #colIdxOrLabel BigDecimal case"(colIdxOrLabel, longs) {
    result.getObject(colIdxOrLabel) >> new BigDecimal(0)
    expect:
    new ResultSetUtil().getLong(result, colIdxOrLabel) == longs

    where:
    colIdxOrLabel | longs
    "id"          | 0
    1             | 0
  }

  def "getLong returns expected #longs with #colIdxOrLabel Integer case"(colIdxOrLabel, longs) {
    result.getObject(colIdxOrLabel) >> new Integer(3)
    expect:
    new ResultSetUtil().getLong(result, colIdxOrLabel) == longs

    where:
    colIdxOrLabel | longs
    "id"          | 3
    1             | 3
  }

  def "getLong returns expected #longs with #colIdxOrLabel Long case"(colIdxOrLabel, longs) {
    result.getObject(colIdxOrLabel) >> new Long(0)
    expect:
    new ResultSetUtil().getLong(result, colIdxOrLabel) == longs

    where:
    colIdxOrLabel | longs
    "id"          | 0
    1             | 0
  }

  def "getLong returns expected #longs with #colIdxOrLabel String case"(colIdxOrLabel, longs) {
    result.getObject(colIdxOrLabel) >> "0"
    expect:
    new ResultSetUtil().getLong(result, colIdxOrLabel) == longs

    where:
    colIdxOrLabel | longs
    "id"          | 0
    1             | 0
  }

  def "getLong returns expected #longs with #colIdxOrLabel empty Object"(colIdxOrLabel, longs) {
    result.getObject(colIdxOrLabel) >> null
    expect:
    new ResultSetUtil().getLong(result, colIdxOrLabel) == longs

    where:
    colIdxOrLabel | longs
    "id"          | null
    1             | null
  }

  def makeResultSet(List<String> aColumns, List... rows) {
    ResultSet result = Mock()
    int currentIndex = -1

    result.next() >> { ++currentIndex < rows.length }
    result./get(String|Short|Date|Int|Timestamp)/(_) >> { String argument ->
      rows[currentIndex][aColumns.indexOf(argument)]
    }

    return result
  }
}
