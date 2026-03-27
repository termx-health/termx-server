package org.termx.core.util.canonical;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.lang3.StringUtils;

public final class GlobMatcher {
  private GlobMatcher() {
  }

  public static boolean matches(String configured, String actual) {
    if (StringUtils.isBlank(configured) || StringUtils.isBlank(actual)) {
      return false;
    }
    if (configured.equals(actual)) {
      return true;
    }
    // If pattern has explicit regex escapes (backslash), treat as regex
    if (isRegexPattern(configured)) {
      if (!hasWildcard(configured) || configured.contains("\\")) {
        return matchesRegex(configured, actual);
      }
    }
    // Glob wildcards: * and ? without regex escapes
    if (hasWildcard(configured)) {
      return Pattern.matches(globToRegex(configured), actual);
    }
    return false;
  }

  public static boolean hasWildcard(String value) {
    return value != null && (value.contains("*") || value.contains("?"));
  }

  public static boolean isRegexPattern(String value) {
    if (value == null) {
      return false;
    }
    return value.contains("[") || value.contains("(") || value.contains("^") || value.contains("$")
        || value.contains("+") || (value.contains(".") && !value.endsWith("."));
  }

  private static boolean matchesRegex(String pattern, String actual) {
    try {
      return Pattern.matches(pattern, actual);
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  public static String globToRegex(String glob) {
    StringBuilder out = new StringBuilder("^");
    for (char c : glob.toCharArray()) {
      switch (c) {
        case '*' -> out.append(".*");
        case '?' -> out.append('.');
        case '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' -> out.append('\\').append(c);
        default -> out.append(c);
      }
    }
    out.append('$');
    return out.toString();
  }
}
