package com.kodality.termx.terminology.fileimporter.fileparser

import spock.lang.Specification

class XlsxFileParserTest extends Specification {

    def "parses CS-anesthesia-types-1.0.0.xlsx and returns expected headers and rows"() {
        given: "XLSX bytes loaded from classpath"
        InputStream is = this.class.classLoader.getResourceAsStream("CS-anesthesia-types-1.0.0.xlsx")
        assert is != null : "Test resource CS-anesthesia-types-1.0.0.xlsx not found on classpath"
        byte[] xlsxBytes = is.bytes

        when: "parsing the XLSX"
        def parser = new XlsxFileParser(xlsxBytes)

        then: "headers are extracted correctly"
        parser.headers == ["code", "display#et", "gender", "gender#system", "parent", "status"]

        and: "row count matches the file (header excluded)"
        parser.rows.size() == 24

        and: "the first and last data rows match exactly"

        parser.rows.first().toList() == [
                "1.",
                "üldanesteesia",
                null,
                null,
                null,
                "active"
        ]

        parser.rows.last().toList() == [
                "3.2.",
                "sedatsioon mujal, täpsustada",
                null,
                null,
                "3.",
                "active"
        ]
    }
}
