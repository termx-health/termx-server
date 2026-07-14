package org.termx.ts.codesystem

import org.termx.ts.PublicationStatus
import spock.lang.Specification

import java.time.LocalDate

/**
 * Migrated from tehik fork KL-96: getFirstVersion()/getLastVersion() must break ties
 * deterministically by version string when multiple versions share a release date.
 */
class CodeSystemVersionSelectionSpec extends Specification {

  private static CodeSystemVersion version(String version, LocalDate releaseDate) {
    return new CodeSystemVersion()
        .setVersion(version)
        .setReleaseDate(releaseDate)
        .setStatus(PublicationStatus.active)
  }

  def "getFirstVersion breaks a release-date tie by lowest version string"() {
    given:
    def sameDate = LocalDate.parse("2024-01-01")
    // input ordered so a release-date-only sort would wrongly return "2.0.0"
    def cs = new CodeSystem().setVersions([version("2.0.0", sameDate), version("1.0.0", sameDate)])

    expect:
    cs.getFirstVersion().map { it.version }.orElse(null) == "1.0.0"
  }

  def "getLastVersion breaks a release-date tie by highest version string"() {
    given:
    def sameDate = LocalDate.parse("2024-01-01")
    // input ordered so a release-date-only sort would wrongly return "1.0.0"
    def cs = new CodeSystem().setVersions([version("1.0.0", sameDate), version("2.0.0", sameDate)])

    expect:
    cs.getLastVersion().map { it.version }.orElse(null) == "2.0.0"
  }
}
