package org.termx.auth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads role names out of an OAuth token payload, given dot-separated claim paths.
 *
 * <p>Providers disagree on where roles live. A flat {@code roles} claim is the simplest case, but
 * Keycloak puts realm roles at {@code realm_access.roles} and per-client roles at
 * {@code resource_access.<client>.roles}, so a single claim name isn't enough.
 */
public final class RoleClaimReader {
  private RoleClaimReader() {}

  /**
   * Split a comma-separated property into claim paths, dropping blanks.
   *
   * <p>Falls back to the flat {@code roles} claim when the property is empty, so a deployment that
   * blanks it out keeps working instead of silently authenticating everyone with no privileges.
   */
  public static List<String> parsePaths(String property) {
    if (property == null || property.isBlank()) {
      return List.of("roles");
    }
    List<String> paths = new ArrayList<>();
    for (String part : property.split(",")) {
      if (!part.isBlank()) {
        paths.add(part.trim());
      }
    }
    return paths.isEmpty() ? List.of("roles") : paths;
  }

  /**
   * Collect role names from every configured path, in order, de-duplicated.
   *
   * <p>A path that is missing, or whose value isn't a list of strings, contributes nothing — token
   * payloads are attacker-influenced, so a malformed claim must not fail authentication with a
   * class-cast; it simply grants no roles.
   */
  public static List<String> readRoles(Map<String, Object> payload, List<String> paths) {
    Set<String> roles = new LinkedHashSet<>();
    if (payload == null || paths == null) {
      return new ArrayList<>(roles);
    }
    for (String path : paths) {
      resolve(payload, path).forEach(roles::add);
    }
    return new ArrayList<>(roles);
  }

  private static List<String> resolve(Map<String, Object> payload, String path) {
    if (path == null || path.isBlank()) {
      return List.of();
    }
    Object current = payload;
    for (String segment : path.trim().split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) {
        return List.of();
      }
      current = map.get(segment);
    }
    if (!(current instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }
}
