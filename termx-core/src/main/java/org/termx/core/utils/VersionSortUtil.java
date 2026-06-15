package org.termx.core.utils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Sorts a resource's versions newest-first, honouring the FHIR R5
 * {@code versionAlgorithm} of each version (TermX stores it in the {@code algorithm} field).
 *
 * <p>Plain string ordering puts {@code "1.0.10"} <em>before</em> {@code "1.0.9"} (lexicographic),
 * which is the bug this fixes. The algorithm codes come from
 * {@code http://hl7.org/fhir/ValueSet/version-algorithm}:
 * <ul>
 *   <li>{@code semver} / {@code integer} / {@code natural} — natural-order comparison (numeric
 *       runs compared as numbers), which orders dotted-numeric and integer versions correctly;</li>
 *   <li>{@code date} — parsed as an ISO date when possible, otherwise natural order;</li>
 *   <li>{@code alpha} — case-insensitive lexicographic;</li>
 *   <li>blank/unknown — natural order (the safe general default).</li>
 * </ul>
 *
 * <p>Primary key is the version string (descending); {@code releaseDate} (descending, nulls last)
 * is the tie-breaker. Mixed-algorithm version lists fall back to natural order across the set so
 * the comparison stays consistent and total.
 */
public final class VersionSortUtil {
  private VersionSortUtil() {}

  public static final String SEMVER = "semver";
  public static final String INTEGER = "integer";
  public static final String DATE = "date";
  public static final String ALPHA = "alpha";
  public static final String NATURAL = "natural";

  /**
   * Returns a new mutable list sorted newest-first. The input is not mutated; {@code null} yields
   * an empty list. Pass accessors for the version string, the algorithm code, and the release date.
   */
  public static <T> List<T> sortDescending(List<T> versions,
                                           Function<T, String> versionFn,
                                           Function<T, String> algorithmFn,
                                           Function<T, LocalDate> releaseDateFn) {
    List<T> result = new ArrayList<>(versions == null ? List.of() : versions);
    if (result.size() < 2) {
      return result;
    }
    String algorithm = resolveCommonAlgorithm(result, algorithmFn);
    Comparator<String> versionComparator = comparator(algorithm);
    Comparator<T> byVersion = Comparator.comparing(versionFn, Comparator.nullsLast(versionComparator));
    Comparator<T> byReleaseDate = Comparator.comparing(releaseDateFn, Comparator.nullsLast(Comparator.naturalOrder()));
    // newest first: reverse both keys.
    result.sort(byVersion.thenComparing(byReleaseDate).reversed());
    return result;
  }

  /** A version-string comparator (ascending) for the given algorithm code. */
  public static Comparator<String> comparator(String algorithm) {
    String algo = algorithm == null ? "" : algorithm.trim().toLowerCase();
    return switch (algo) {
      case ALPHA -> String.CASE_INSENSITIVE_ORDER;
      case DATE -> VersionSortUtil::compareDate;
      // semver / integer / natural / unknown all sort correctly under natural order.
      default -> VersionSortUtil::compareNatural;
    };
  }

  /**
   * When every version declares the same algorithm, use it. Otherwise (mixed or partial) fall back
   * to natural order so the comparator stays consistent across the whole list.
   */
  private static <T> String resolveCommonAlgorithm(List<T> versions, Function<T, String> algorithmFn) {
    String common = null;
    for (T v : versions) {
      String a = algorithmFn.apply(v);
      a = a == null ? "" : a.trim().toLowerCase();
      if (a.isEmpty()) {
        return NATURAL;
      }
      if (common == null) {
        common = a;
      } else if (!common.equals(a)) {
        return NATURAL;
      }
    }
    return common == null ? NATURAL : common;
  }

  private static int compareDate(String a, String b) {
    LocalDate da = tryParseDate(a);
    LocalDate db = tryParseDate(b);
    if (da != null && db != null) {
      return da.compareTo(db);
    }
    if (da != null) {
      return 1; // a parses, b doesn't — treat parseable as greater (newer-looking)
    }
    if (db != null) {
      return -1;
    }
    return compareNatural(a, b);
  }

  private static LocalDate tryParseDate(String s) {
    if (s == null || s.length() < 10) {
      return null;
    }
    try {
      return LocalDate.parse(s.substring(0, 10));
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Natural-order comparison: split each string into maximal digit and non-digit runs and compare
   * run-by-run, numerically where both runs are digits. Numeric runs are compared by value (so
   * {@code "9" < "10"}) with leading zeros broken by length as a last resort.
   */
  static int compareNatural(String a, String b) {
    if (a == null || b == null) {
      return a == null ? (b == null ? 0 : -1) : 1;
    }
    int i = 0;
    int j = 0;
    int lenA = a.length();
    int lenB = b.length();
    while (i < lenA && j < lenB) {
      char ca = a.charAt(i);
      char cb = b.charAt(j);
      boolean da = Character.isDigit(ca);
      boolean db = Character.isDigit(cb);
      if (da && db) {
        int startA = i;
        int startB = j;
        while (i < lenA && Character.isDigit(a.charAt(i))) {
          i++;
        }
        while (j < lenB && Character.isDigit(b.charAt(j))) {
          j++;
        }
        String numA = stripLeadingZeros(a.substring(startA, i));
        String numB = stripLeadingZeros(b.substring(startB, j));
        if (numA.length() != numB.length()) {
          return numA.length() - numB.length();
        }
        int cmp = numA.compareTo(numB);
        if (cmp != 0) {
          return cmp;
        }
      } else {
        int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
        if (cmp != 0) {
          return cmp;
        }
        i++;
        j++;
      }
    }
    return (lenA - i) - (lenB - j);
  }

  private static String stripLeadingZeros(String num) {
    int k = 0;
    while (k < num.length() - 1 && num.charAt(k) == '0') {
      k++;
    }
    return num.substring(k);
  }
}
