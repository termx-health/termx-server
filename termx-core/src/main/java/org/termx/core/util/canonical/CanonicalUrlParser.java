package org.termx.core.util.canonical;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public class CanonicalUrlParser {
  private final String baseUrl;
  private final String version;
  private final Map<String, String> queryParams;

  private CanonicalUrlParser(String baseUrl, String version, Map<String, String> queryParams) {
    this.baseUrl = baseUrl;
    this.version = version;
    this.queryParams = queryParams;
  }

  static int lastAuthoritativeQueryDelimiter(String s) {
    if (StringUtils.isBlank(s)) {
      return -1;
    }
    int last = -1;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == '?' && isValidAuthoritativeQuerySuffix(s.substring(i + 1))) {
        last = i;
      }
    }
    return last;
  }

  static boolean isValidAuthoritativeQuerySuffix(String suffix) {
    if (StringUtils.isBlank(suffix)) {
      return false;
    }
    for (String part : suffix.split("&", -1)) {
      if (part.isEmpty()) {
        return false;
      }
      int eq = part.indexOf('=');
      if (eq <= 0) {
        return false;
      }
      String key = part.substring(0, eq);
      if (!key.matches("[a-zA-Z_][a-zA-Z0-9_.-]*")) {
        return false;
      }
    }
    return true;
  }

  public static CanonicalUrlParser parse(String canonical) {
    if (StringUtils.isBlank(canonical)) {
      return new CanonicalUrlParser("", null, new HashMap<>());
    }

    String remaining = canonical;
    String version = null;
    Map<String, String> queryParams = new HashMap<>();

    int versionIdx = remaining.indexOf('|');
    if (versionIdx >= 0) {
      version = remaining.substring(versionIdx + 1);
      remaining = remaining.substring(0, versionIdx);
    }

    int queryIdx = lastAuthoritativeQueryDelimiter(remaining);
    if (queryIdx >= 0) {
      String queryString = remaining.substring(queryIdx + 1);
      remaining = remaining.substring(0, queryIdx);
      if (queryIdx > 0 && remaining.endsWith("\\")) {
        remaining = remaining.substring(0, remaining.length() - 1);
      }
      for (String param : queryString.split("&")) {
        int eqIdx = param.indexOf('=');
        if (eqIdx > 0) {
          String key = param.substring(0, eqIdx);
          String value = param.substring(eqIdx + 1);
          queryParams.put(key, value);
        }
      }
    }

    return new CanonicalUrlParser(remaining, version, queryParams);
  }

  public String getStatus() {
    return queryParams.get("status");
  }

  public boolean matchesFilters(Map<String, String> metadata) {
    if (queryParams.isEmpty()) {
      return true;
    }
    for (Map.Entry<String, String> filter : queryParams.entrySet()) {
      String actualValue = metadata.get(filter.getKey());
      if (!filter.getValue().equals(actualValue)) {
        return false;
      }
    }
    return true;
  }
}
