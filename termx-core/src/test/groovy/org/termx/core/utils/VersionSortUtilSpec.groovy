package org.termx.core.utils

import spock.lang.Specification
import java.time.LocalDate
import java.util.function.Function

/**
 * Covers {@link VersionSortUtil}, the algorithm-aware, newest-first version sorter applied when a
 * resource returns its versions list. The headline case is the reported bug: "1.0.10" must sort
 * ABOVE "1.0.9"/"1.0.8" (numeric), not below them (lexicographic).
 */
class VersionSortUtilSpec extends Specification {

  static Function<Map, String> VERSION = { it.version } as Function
  static Function<Map, String> ALGO = { it.algorithm } as Function
  static Function<Map, LocalDate> RELEASE = { it.releaseDate } as Function

  private static List<String> sortedVersions(List<Map> versions) {
    return VersionSortUtil.sortDescending(versions, VERSION, ALGO, RELEASE).collect { it.version }
  }

  def "semver versions sort newest-first numerically (1.0.10 above 1.0.9)"() {
    given:
    def versions = [[version: '1.0.9', algorithm: 'semver'],
                    [version: '1.0.10', algorithm: 'semver'],
                    [version: '1.0.8', algorithm: 'semver']]

    expect:
    sortedVersions(versions) == ['1.0.10', '1.0.9', '1.0.8']
  }

  def "blank/unknown algorithm falls back to natural order (still numeric-aware)"() {
    given:
    def versions = [[version: '1.0.9', algorithm: null],
                    [version: '1.0.10', algorithm: null],
                    [version: '1.0.8', algorithm: '']]

    expect:
    sortedVersions(versions) == ['1.0.10', '1.0.9', '1.0.8']
  }

  def "integer algorithm orders numerically, not lexically"() {
    given:
    def versions = [[version: '2', algorithm: 'integer'],
                    [version: '10', algorithm: 'integer'],
                    [version: '9', algorithm: 'integer']]

    expect:
    sortedVersions(versions) == ['10', '9', '2']
  }

  def "date algorithm orders chronologically newest-first"() {
    given:
    def versions = [[version: '2026-01-05', algorithm: 'date'],
                    [version: '2026-02-01', algorithm: 'date'],
                    [version: '2025-12-31', algorithm: 'date']]

    expect:
    sortedVersions(versions) == ['2026-02-01', '2026-01-05', '2025-12-31']
  }

  def "alpha algorithm is case-insensitive lexicographic, newest-first"() {
    given:
    def versions = [[version: 'alpha', algorithm: 'alpha'],
                    [version: 'Charlie', algorithm: 'alpha'],
                    [version: 'bravo', algorithm: 'alpha']]

    expect:
    sortedVersions(versions) == ['Charlie', 'bravo', 'alpha']
  }

  def "mixed algorithms across the list fall back to a consistent natural order"() {
    given:
    def versions = [[version: '1.0.9', algorithm: 'semver'],
                    [version: '1.0.10', algorithm: 'integer'],
                    [version: '1.0.2', algorithm: 'date']]

    expect:
    sortedVersions(versions) == ['1.0.10', '1.0.9', '1.0.2']
  }

  def "release date breaks ties when version strings are equal, newest-first"() {
    given:
    def versions = [[version: '1.0.0', algorithm: 'semver', releaseDate: LocalDate.of(2025, 1, 1)],
                    [version: '1.0.0', algorithm: 'semver', releaseDate: LocalDate.of(2026, 1, 1)]]

    when:
    def sorted = VersionSortUtil.sortDescending(versions, VERSION, ALGO, RELEASE)

    then:
    sorted*.releaseDate == [LocalDate.of(2026, 1, 1), LocalDate.of(2025, 1, 1)]
  }

  def "null and singleton lists are handled"() {
    expect:
    VersionSortUtil.sortDescending(null, VERSION, ALGO, RELEASE) == []
    sortedVersions([[version: '1.0.0', algorithm: 'semver']]) == ['1.0.0']
  }
}
