package org.termx.snomed.integration

import spock.lang.Specification
import spock.lang.Unroll

/**
 * The SNOMED delta requires RF2 zips. A non-RF2 object in the SNOMED container (e.g. the JSON
 * nomenclature file that triggered the reported crash) must be rejected before the delta-generator
 * subprocess is spawned.
 */
class SnomedDeltaCalculateServiceSpec extends Specification {

  @Unroll
  def "isRf2Archive(#filename, #contentType) == #expected"() {
    expect:
    SnomedDeltaCalculateService.isRf2Archive(filename, contentType) == expected

    where:
    filename                                                       | contentType        || expected
    "lt-lab-klt-nomenclature--1.0.6-PROD.json"                     | "application/json" || false   // the reported bug
    "SnomedCT_InternationalRF2_PRODUCTION_20260501T120000Z.zip"    | "application/zip"  || true
    "SnomedCT_Edition.ZIP"                                         | null               || true    // case-insensitive name
    "no-extension-name"                                            | "application/zip"  || true    // content-type says zip
    "archive.json"                                                 | null               || false
    null                                                           | "application/json" || false
    null                                                           | null               || false
  }
}
