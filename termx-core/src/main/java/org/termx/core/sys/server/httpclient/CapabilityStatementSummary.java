package org.termx.core.sys.server.httpclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.kodality.commons.util.JsonUtil;
import org.termx.sys.server.ServerConnectionCheckResult;
import org.apache.commons.lang3.StringUtils;

/**
 * Best-effort extraction of {@code software.name} / {@code software.version} / {@code fhirVersion} from a
 * FHIR {@code CapabilityStatement} response body into a {@link ServerConnectionCheckResult}. A non-FHIR or
 * non-JSON body is tolerated silently — connectivity success does not depend on the body being parseable.
 */
public final class CapabilityStatementSummary {
  private CapabilityStatementSummary() {}

  public static void apply(String body, ServerConnectionCheckResult result) {
    if (StringUtils.isBlank(body)) {
      return;
    }
    try {
      JsonNode node = JsonUtil.getObjectMapper().readTree(body);
      JsonNode software = node.get("software");
      if (software != null) {
        if (software.hasNonNull("name")) {
          result.setSoftware(software.get("name").asText());
        }
        if (software.hasNonNull("version")) {
          result.setSoftwareVersion(software.get("version").asText());
        }
      }
      if (node.hasNonNull("fhirVersion")) {
        result.setFhirVersion(node.get("fhirVersion").asText());
      }
    } catch (Exception ignored) {
      // best-effort only
    }
  }
}
