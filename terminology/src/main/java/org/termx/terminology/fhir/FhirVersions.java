package org.termx.terminology.fhir;

import io.micronaut.core.util.StringUtils;
import java.util.List;

/**
 * Semantic-version matching for FHIR terminology, mirroring the reference engine
 * ({@code org.hl7.fhir.utilities.VersionUtilities} in {@code org.hl7.fhir.core}, the implementation behind the
 * tx-ecosystem tests). A code system version carried by a {@code ValueSet.compose.include.version}, a
 * {@code Coding.version}, or a {@code system-version}/{@code force-system-version}/{@code check-system-version}
 * parameter may be a concrete version ({@code 1.0.0}), a wildcard pattern ({@code 1.x.x}, {@code 1.0.x}), or a
 * trailing-{@code ?} pattern ({@code 1.0?}); a value that is NOT valid semver-with-wildcards (e.g. a bare
 * {@code 1}) is treated as an exact literal — so {@code 1} does not match {@code 1.0.0}.
 */
public final class FhirVersions {
  private FhirVersions() {
  }

  private record Semver(String major, String minor, String patch, String label, String build, boolean valid) {
  }

  /**
   * Parses {@code major.minor.patch[-label][+build]}. With {@code allowWildcards}, the numeric parts may be a
   * wildcard token ({@code x}/{@code X}/{@code *}); otherwise they must be numeric. Major is required; minor and
   * patch are optional (a criteria may stop early, e.g. {@code 1.x}).
   */
  private static Semver parse(String v, boolean allowWildcards) {
    if (StringUtils.isEmpty(v)) {
      return new Semver(null, null, null, null, null, false);
    }
    String rest = v;
    String build = null;
    String label = null;
    int plus = rest.indexOf('+');
    if (plus >= 0) {
      build = rest.substring(plus + 1);
      rest = rest.substring(0, plus);
    }
    int dash = rest.indexOf('-');
    if (dash >= 0) {
      label = rest.substring(dash + 1);
      rest = rest.substring(0, dash);
    }
    String[] parts = rest.split("\\.", -1);
    if (parts.length == 0 || parts.length > 3) {
      return new Semver(null, null, null, null, null, false);
    }
    for (String p : parts) {
      if (!isNumericOrWildcard(p, allowWildcards)) {
        return new Semver(null, null, null, null, null, false);
      }
    }
    String major = parts[0];
    String minor = parts.length > 1 ? parts[1] : null;
    String patch = parts.length > 2 ? parts[2] : null;
    return new Semver(major, minor, patch, label, build, true);
  }

  private static boolean isNumericOrWildcard(String p, boolean allowWildcards) {
    if (StringUtils.isEmpty(p)) {
      return false;
    }
    if (allowWildcards && (p.equals("x") || p.equals("X") || p.equals("*"))) {
      return true;
    }
    return p.chars().allMatch(Character::isDigit);
  }

  /** Whether {@code v} is a valid semantic version; {@code allowWildcards} permits {@code x}/{@code X}/{@code *} parts. */
  public static boolean isSemVer(String v, boolean allowWildcards) {
    return parse(v, allowWildcards).valid();
  }

  /** Whether {@code v} parses as semver AND actually carries a wildcard ({@code x}/{@code X}/{@code *} or trailing {@code ?}). */
  public static boolean isSemVerWithWildcards(String v) {
    if (v != null && v.endsWith("?")) {
      return isSemVer(v.substring(0, v.length() - 1), true) && versionHasWildcards(v);
    }
    return isSemVer(v, true) && versionHasWildcards(v);
  }

  /** Whether {@code v} contains a wildcard token: {@code *} anywhere, trailing {@code ?}, or {@code x}/{@code X} in the numeric part. */
  public static boolean versionHasWildcards(String v) {
    if (StringUtils.isEmpty(v)) {
      return false;
    }
    if (v.endsWith("?") || v.contains("*")) {
      return true;
    }
    String numeric = v;
    int dash = numeric.indexOf('-');
    int plus = numeric.indexOf('+');
    if (dash >= 0 && plus >= 0) {
      numeric = numeric.substring(0, Math.min(dash, plus));
    } else if (dash >= 0) {
      numeric = numeric.substring(0, dash);
    } else if (plus >= 0) {
      numeric = numeric.substring(0, plus);
    }
    return numeric.contains("x") || numeric.contains("X");
  }

  /**
   * Whether the concrete {@code candidate} version satisfies the {@code criteria} pattern. A wildcard part
   * ({@code x}/{@code X}/{@code *}) matches any present value; a trailing {@code ?} makes the remaining parts
   * optional; everything else must be exactly equal. A {@code criteria} that is not valid semver-with-wildcards
   * is compared as an exact literal — so {@code "1"} does NOT match {@code "1.0.0"}.
   */
  public static boolean versionMatches(String criteria, String candidate) {
    if (StringUtils.isEmpty(criteria) || StringUtils.isEmpty(candidate)) {
      return false;
    }
    boolean endsWithQ = criteria.endsWith("?");
    String crit = endsWithQ ? criteria.substring(0, criteria.length() - 1) : criteria;
    if (crit.equals("*") || crit.equals("x") || crit.equals("X")) {
      return true;
    }
    Semver pc = parse(crit, true);
    Semver pv = parse(candidate, false);
    if (!pc.valid() || !pv.valid()) {
      return criteria.equals(candidate);
    }
    if (!partMatches(pc.major(), pv.major())) {
      return false;
    }
    if (endsWithQ && pc.minor() == null) {
      return true;
    }
    if (!partMatches(pc.minor(), pv.minor())) {
      return false;
    }
    if (endsWithQ && pc.patch() == null) {
      return true;
    }
    if (!partMatches(pc.patch(), pv.patch())) {
      return false;
    }
    if (endsWithQ && pc.label() == null && pc.build() == null) {
      return true;
    }
    return partMatches(pc.label(), pv.label()) && partMatches(pc.build(), pv.build());
  }

  private static boolean partMatches(String criteria, String candidate) {
    if (criteria == null) {
      return candidate == null;
    }
    if (criteria.equals("x") || criteria.equals("X") || criteria.equals("*")) {
      return candidate != null;
    }
    return criteria.equals(candidate);
  }

  /** True if any version in {@code candidates} matches {@code criteria}. */
  public static boolean versionMatchesList(String criteria, List<String> candidates) {
    return candidates != null && candidates.stream().anyMatch(c -> versionMatches(criteria, c));
  }

  /**
   * Whether {@code candidate} is a strictly more detailed form of the {@code criteria} pattern — i.e. criteria
   * is a wildcard semver, candidate is a concrete semver, and candidate matches it (so {@code 1.0.0} is "more
   * detailed than" {@code 1.x.x}, and the concrete coding version should replace the pattern).
   */
  public static boolean isMoreDetailed(String criteria, String candidate) {
    return isSemVerWithWildcards(criteria) && isSemVer(candidate, false) && versionMatches(criteria, candidate);
  }
}
