package org.termx.terminology.fileimporter.codesystem.utils

import spock.lang.Specification

import java.nio.charset.StandardCharsets

/** A delimited cell gives each code once, trimmed, with no empty part. */
class CodeSystemFileImportProcessorSplitSpec extends Specification {

  def "a delimited cell yields each part once, trimmed, without empty parts"() {
    given:
    def request = new CodeSystemFileImportRequest()
    request.type = "csv"
    request.properties = [
        new CodeSystemFileImportRequest.FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
        new CodeSystemFileImportRequest.FileProcessingProperty(columnName: "name", propertyName: "display", propertyType: "designation", language: "en"),
        new CodeSystemFileImportRequest.FileProcessingProperty(columnName: "specimen", propertyName: "specimen", propertyType: "string", propertyDelimiter: ","),
    ]
    def csv = 'code,name,specimen\nA,Alpha,"S, P, S, "\n'

    when:
    def result = CodeSystemFileImportProcessor.process(request, csv.getBytes(StandardCharsets.UTF_8))
    def specimens = result.entities.collectMany { it.get("specimen") ?: [] }*.value

    then:
    specimens == ["S", "P"]
  }
}
