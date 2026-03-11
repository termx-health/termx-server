package com.kodality.commons.csv.parser


import spock.lang.Specification

import java.time.LocalDate

class CsvReaderTest extends Specification {

  def csvFile = """
h1;h2;h3
v1;v2;v3
"""

  def csvFileEmptyColumnName = """
h1;h2;;h4
v1;v2;;;
x1;;;x4
"""

  def csvFileWithDifferentDataTypes = """
h1;h2;;h4
03.05.2002;3.14;;;
10.11.2012;1.44;;x4
"""

  def differentCaseExamples = """
key1;Key2;KEY3
foo;bar;baz
"""
  
  def "test number of headers"() {
    CsvReader reader = new CsvReader()
    com.kodality.commons.csv.record.CsvRecordList result = reader.parse(new ByteArrayInputStream(csvFile.bytes))
    expect:
    result.headers.size() == 3
  }

  def "test number of headers with empty"() {
    CsvReader reader = new CsvReader()
    com.kodality.commons.csv.record.CsvRecordList result = reader.parse(new ByteArrayInputStream(csvFileEmptyColumnName.bytes))
    expect:
    result.headers.size() == 4
  }

  def "test records"() {
    CsvReader reader = new CsvReader()
    com.kodality.commons.csv.record.CsvRecordList result = reader.parse(new ByteArrayInputStream(csvFileWithDifferentDataTypes.bytes))
    def records = result.records

    expect:
    records.size() == 2
    records.get(0).getAsLocalDate('h1') == LocalDate.of(2002, 5, 3)
    records.get(0).getAsBigDecimal('h2') == BigDecimal.valueOf(3.14)
  }

  def "should treat keys in case insensitive manner"() {
    CsvReader reader = new CsvReader()
    com.kodality.commons.csv.record.CsvRecordList result = reader.parse(new ByteArrayInputStream(differentCaseExamples.bytes))
    def records = result.records

    expect:
    records.get(0).get('key1') == 'foo'
    records.get(0).get('KEY1') == 'foo'
    records.get(0).get('key2') == 'bar'
    records.get(0).get('KEY2') == 'bar'
    records.get(0).get('key3') == 'baz'
    records.get(0).get('KEY3') == 'baz'
    records.get(0).get('kEy3') == 'baz'
  }


}
