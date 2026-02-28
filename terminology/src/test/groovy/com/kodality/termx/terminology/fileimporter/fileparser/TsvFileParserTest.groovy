package com.kodality.termx.terminology.fileimporter.fileparser

import spock.lang.Specification

class TsvFileParserTest extends Specification {

    def "parses CS-anesthesia-types-1.0.0.tsv and returns expected headers and rows"() {
        given: "TSV bytes loaded from classpath"
        InputStream is = this.class.classLoader.getResourceAsStream("CS-anesthesia-types-1.0.0.tsv")
        assert is != null : "Test resource CS-anesthesia-types-1.0.0.tsv not found on classpath"
        byte[] tsvBytes = is.bytes

        when: "parsing the TSV"
        def parser = new TsvFileParser(tsvBytes)

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
