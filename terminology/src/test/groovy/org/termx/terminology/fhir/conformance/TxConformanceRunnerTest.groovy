package org.termx.terminology.fhir.conformance

import org.termx.ts.conformance.TxConformanceRunRequest
import spock.lang.Specification

import java.nio.file.Path

class TxConformanceRunnerTest extends Specification {
  def runner = new TxConformanceRunner("/opt/validator_cli.jar", "http://termx.local/fhir")

  def "buildCommand wires txTests with -tx, -output and the optional flags"() {
    given:
    def req = new TxConformanceRunRequest().setSuite("simple-cases").setMode("general")

    when:
    def cmd = runner.buildCommand(Path.of("/opt/validator_cli.jar"), Path.of("/tmp/out"), req, null)

    then:
    cmd.contains("-jar")
    cmd.contains("/opt/validator_cli.jar")
    cmd.contains("txTests")
    cmd[cmd.indexOf("-tx") + 1] == "http://termx.local/fhir"
    cmd[cmd.indexOf("-output") + 1] == "/tmp/out"
    cmd[cmd.indexOf("-suite") + 1] == "simple-cases"
    cmd[cmd.indexOf("-mode") + 1] == "general"
    !cmd.contains("-filter")
    !cmd.contains("-input")
  }

  def "buildCommand adds -input when a custom bundle is supplied"() {
    when:
    def cmd = runner.buildCommand(Path.of("/opt/validator_cli.jar"), Path.of("/tmp/out"),
        new TxConformanceRunRequest(), Path.of("/tmp/bundle.tgz"))

    then:
    cmd[cmd.indexOf("-input") + 1] == "/tmp/bundle.tgz"
  }

  def "summarize counts passed/failed and reads result+score from a FHIR TestReport"() {
    given:
    def report = '''{
      "resourceType":"TestReport","result":"fail","score":0.6667,
      "test":[
        {"name":"a","action":[{"operation":{"result":"pass"}}]},
        {"name":"b","action":[{"operation":{"result":"pass"}}]},
        {"name":"c","action":[{"operation":{"result":"fail"}}]}
      ]}'''

    expect:
    TxConformanceRunner.summarize(report) == "result=fail score=0.667 passed=2 failed=1"
  }

  def "summarize is null-safe on a malformed report"() {
    expect:
    TxConformanceRunner.summarize("not json").startsWith("unparseable report")
  }
}
