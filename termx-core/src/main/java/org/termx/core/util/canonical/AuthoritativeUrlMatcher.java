package org.termx.core.util.canonical;

import org.apache.commons.lang3.StringUtils;

/**
 * Matches resource canonical URLs against ecosystem authoritative patterns (exact, glob, regex, type
 * endpoint, or regex + {@code \?status=active}-style query split in {@link CanonicalUrlParser}).
 */
public final class AuthoritativeUrlMatcher {

  private AuthoritativeUrlMatcher() {}

  /**
   * @param configuredPattern authoritative entry (may include query; base may be regex or type endpoint)
   * @param resourceCanonicalUrl the canonical URL of the resource to check
   * @param fhirResourceType e.g. CodeSystem, ValueSet
   */
  public static boolean matches(String configuredPattern, String resourceCanonicalUrl, String fhirResourceType) {
    if (StringUtils.isBlank(configuredPattern) || StringUtils.isBlank(resourceCanonicalUrl)) {
      return false;
    }
    if ("*".equals(configuredPattern)) {
      return true;
    }
    CanonicalUrlParser parsed = CanonicalUrlParser.parse(configuredPattern);
    String base = parsed.getBaseUrl();
    if (StringUtils.isBlank(base)) {
      return false;
    }
    if (isTypeCollectionEndpoint(base, fhirResourceType)) {
      String prefix = base.replaceAll("/+$", "");
      return resourceCanonicalUrl.equals(prefix) || resourceCanonicalUrl.startsWith(prefix + "/");
    }
    return GlobMatcher.matches(base, resourceCanonicalUrl);
  }

  /**
   * True when {@code baseUrl} is a FHIR type collection URL (ends with {@code /CodeSystem}, etc.) with no
   * wildcards — enables {@code https://host/CodeSystem?status=active} semantics.
   */
  public static boolean isTypeCollectionEndpoint(String baseUrl, String fhirResourceType) {
    if (StringUtils.isBlank(baseUrl) || StringUtils.isBlank(fhirResourceType) || baseUrl.contains("*")) {
      return false;
    }
    int scheme = baseUrl.indexOf("://");
    if (scheme < 0) {
      return false;
    }
    int pathStart = baseUrl.indexOf('/', scheme + 3);
    if (pathStart < 0) {
      return false;
    }
    String path = baseUrl.substring(pathStart);
    int q = path.indexOf('?');
    if (q >= 0) {
      path = path.substring(0, q);
    }
    path = path.replaceAll("/+$", "");
    if (path.isEmpty()) {
      return false;
    }
    int slash = path.lastIndexOf('/');
    String lastSeg = slash >= 0 ? path.substring(slash + 1) : path;
    return fhirResourceType.equals(lastSeg);
  }
}
