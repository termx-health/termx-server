package org.termx.snomed.integration

import spock.lang.Specification

/**
 * The upstream delta-generator-tool crashes with ArrayIndexOutOfBoundsException in createArchive when
 * the current archive has no components newer than the baseline (an empty delta). We detect that from
 * its log so the user gets an actionable message instead of the raw stack trace.
 */
class SnomedDeltaGeneratorRuntimeSpec extends Specification {

  def "isEmptyDelta detects the upstream 0-component crash (the reported error)"() {
    given: "the tail of the failing run reported by the user"
    def log = """Previously seen effective times collected for 12404009 components
First pass identifying latest state from /tmp/snomed-delta-1234/current.zip
Latest versions collected for 0 components
Extracting new rows from /tmp/snomed-delta-1234/current.zip
Creating archive from /tmp/DeltaGeneratorTool9999
Exception in thread "main" java.lang.ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0"""

    expect:
    SnomedDeltaGeneratorRuntime.isEmptyDelta(log)
  }

  def "isEmptyDelta is false for a real (non-empty) delta"() {
    given:
    def log = """Previously seen effective times collected for 12404009 components
Latest versions collected for 15342 components
Rows exported: 15342"""

    expect: "the baseline's '...collected for 12404009 components' line must not trigger a false positive"
    !SnomedDeltaGeneratorRuntime.isEmptyDelta(log)
  }

  def "isEmptyDelta is false when the marker line is absent or input is null"() {
    expect:
    !SnomedDeltaGeneratorRuntime.isEmptyDelta("some unrelated tool output")
    !SnomedDeltaGeneratorRuntime.isEmptyDelta(null)
  }
}
