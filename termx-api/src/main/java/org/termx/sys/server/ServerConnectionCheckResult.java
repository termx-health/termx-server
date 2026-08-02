package org.termx.sys.server;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Result of a live connectivity check against a {@link TerminologyServer}. The check performs a single
 * lightweight read (the FHIR {@code CapabilityStatement} for FHIR-kind servers) using the same root URL,
 * headers and authentication as real calls, so a success proves the configured endpoint is reachable and
 * readable with the configured credentials.
 */
@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class ServerConnectionCheckResult {
  /** {@code true} when the probe returned a 2xx response. */
  private boolean success;
  /** HTTP status code returned by the server (may be set even on failure, e.g. 401/403/404). */
  private Integer statusCode;
  /** The URL that was probed. */
  private String url;
  /** Round-trip duration in milliseconds. */
  private Long durationMs;
  /** CapabilityStatement {@code software.name} (FHIR-kind servers only), e.g. "Ontoserver". */
  private String software;
  /** CapabilityStatement {@code software.version} (FHIR-kind servers only). */
  private String softwareVersion;
  /** CapabilityStatement {@code fhirVersion} (FHIR-kind servers only). */
  private String fhirVersion;
  /** Failure detail when {@link #success} is {@code false}. */
  private String error;
}
