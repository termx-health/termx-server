package com.kodality.termserver.commons.util

import spock.lang.Specification

class PipeUtilTest extends Specification {
  def "from pipe"() {
    expect:
    vals == PipeUtil.parsePipe(pipeStr)
    where:
    pipeStr   | vals
    ""        | [null, null] as String[]
    null      | [null, null] as String[]
    "a:b:c|d" | ["a:b:c", "d"] as String[]
    "e"       | [null, "e"] as String[]
  }

  def "to pipe"() {
    expect:
    pipeStr == PipeUtil.toPipe(vals)
    where:
    pipeStr   | vals
    null      | [null, null] as String[]
    "v"       | [null, "v"] as String[]
    "a:b:c|d" | ["a:b:c", "d"] as String[]
  }
}
