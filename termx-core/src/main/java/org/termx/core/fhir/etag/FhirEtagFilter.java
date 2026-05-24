package org.termx.core.fhir.etag;

import com.kodality.kefhir.rest.filter.KefhirRequestFilter;
import com.kodality.kefhir.rest.filter.KefhirResponseFilter;
import com.kodality.kefhir.rest.model.KefhirRequest;
import com.kodality.kefhir.rest.model.KefhirResponse;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.kefhir.structure.api.ResourceRepresentation;
import com.kodality.kefhir.structure.service.ResourceFormatService;
import io.micronaut.http.MediaType;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r5.model.Resource;

/**
 * Emits weak {@code ETag} validators on FHIR read responses and honours inbound
 * {@code If-None-Match} headers by short-circuiting to {@code 304 Not Modified}.
 *
 * <p>Background: TermX FHIR reads currently emit
 * {@code Cache-Control: no-cache, no-store, max-age=0, must-revalidate}, which forbids
 * any HTTP cache from storing the response body. Consumers that want to revalidate
 * (e.g. an HTTP reverse-proxy in front of TermX, or a long-lived browser session) have
 * no validator to revalidate against — every request is a full re-fetch, paying the
 * resource-build cost even when nothing has changed since the last fetch.
 *
 * <p>This filter pair makes FHIR reads cache-friendly without softening freshness
 * semantics:
 *
 * <ol>
 *   <li>On a {@code GET}/{@code HEAD} request that arrives with {@code If-None-Match},
 *       stash the header value on the {@link KefhirRequest} so the response filter can
 *       see it after the resource has been built.
 *   <li>After kefhir resolves the response body (a FHIR resource POJO), serialise it
 *       through the project's existing {@link ResourceFormatService} using the
 *       request's preferred content type, then compute a weak ETag of the form
 *       {@code W/"<crc32-hex>-<byte-length>"} over the resulting bytes.
 *   <li>If the supplied {@code If-None-Match} matches (exact, wildcard {@code *}, or a
 *       comma-separated list per RFC 9110 §13.1.2), rewrite the response to
 *       {@code 304 Not Modified} with no body. The ETag header is echoed so the cache
 *       can refresh its TTL on the existing stored entry.
 *   <li>On a miss, attach the ETag + a revalidating {@code Cache-Control}
 *       ({@code public, max-age=0, must-revalidate}) so caches may store but must
 *       revalidate before serving. Any prior {@code Pragma: no-cache} or
 *       {@code Expires: 0} is dropped so downstream caches don't see contradictory
 *       directives.
 * </ol>
 *
 * <p>Why <b>weak</b> ETag: FHIR resource serialisation is not byte-deterministic across
 * runs — element ordering inside arrays, JSON key ordering, and pretty-printing may
 * vary between equivalent representations. Strong validators would produce false misses
 * on each variation. Weak validators (RFC 9110 §8.8.1) explicitly allow matching across
 * such equivalent representations.
 *
 * <p>Scope:
 *
 * <ul>
 *   <li>{@code GET} / {@code HEAD} only. Mutating methods aren't cacheable.
 *   <li>Status 200 only. Errors get no validator — caches shouldn't memoize a
 *       transient 4xx / 5xx.
 *   <li>Path-agnostic: any FHIR read route kefhir dispatches via
 *       {@code RuleThemAllFhirController} is covered. The filter sees only
 *       {@link KefhirRequest} / {@link KefhirResponse}, which are always FHIR
 *       requests by construction.
 * </ul>
 *
 * <p>Order: {@code 100}. After kefhir's built-in request/response filters (which run in
 * the {@code 1..50} band by convention) so we see the post-validated, post-formatted
 * request shape.
 */
@Singleton
@Slf4j
@RequiredArgsConstructor
public class FhirEtagFilter implements KefhirRequestFilter, KefhirResponseFilter {

  /** Request property under which the request filter stashes the inbound header. */
  static final String IF_NONE_MATCH_PROPERTY = "fhir-etag.if-none-match";

  private static final Integer ORDER = 100;
  private static final String CACHE_CONTROL_REVALIDATING = "public, max-age=0, must-revalidate";
  private static final String IF_NONE_MATCH_ANY = "*";

  private final ResourceFormatService resourceFormatService;

  @Override
  public Integer getOrder() {
    return ORDER;
  }

  // -- Request side -----------------------------------------------------------

  /**
   * Capture {@code If-None-Match} for the response side. We stash on the request
   * property bag (used by other kefhir filters for cross-phase state) rather than
   * relying on the response side re-reading the request — kefhir mutates the
   * request object as it travels through filter phases and we want the original
   * client-supplied value.
   */
  @Override
  public void handleRequest(KefhirRequest request) {
    if (!isReadShape(request)) {
      return;
    }
    String header = request.getHeader("If-None-Match");
    if (header != null && !header.isBlank()) {
      request.getProperties().put(IF_NONE_MATCH_PROPERTY, header);
    }
  }

  // -- Response side ----------------------------------------------------------

  /**
   * Compute the ETag, decide between {@code 304} and the standard {@code 200 + ETag},
   * and rewrite cache-related headers either way.
   *
   * <p>Note: kefhir's {@code RuleThemAllFhirController.execute} runs response filters
   * <b>before</b> the body is serialised onto the wire. We serialise here ourselves
   * (via {@link ResourceFormatService}) to compute the hash. On a {@code 304} short-
   * circuit we drop the body, so {@code readKefhirResponse} downstream serialises
   * nothing. On a miss we keep the body and accept one extra serialisation pass —
   * cheap relative to the resource-build cost we're trying to elide on revalidation.
   */
  @Override
  public void handleResponse(KefhirResponse response, KefhirRequest request) {
    if (!isReadShape(request)) {
      return;
    }
    Integer status = response.getStatus();
    if (status == null || status != 200) {
      return;
    }
    Object body = response.getBody();
    if (body == null) {
      return;
    }

    String etag = computeWeakEtag(body, request);
    if (etag == null) {
      return; // body not formattable to bytes (unknown content type, etc.) — leave the response untouched.
    }

    Object stashed = request.getProperties().get(IF_NONE_MATCH_PROPERTY);
    String ifNoneMatch = stashed instanceof String s ? s : null;

    if (matchesIfNoneMatch(ifNoneMatch, etag)) {
      response.setStatus(304);
      response.setBody(null);
      response.header("ETag", etag);
      response.header("Cache-Control", CACHE_CONTROL_REVALIDATING);
      clearNoStoreHeaders(response);
      log.debug("FHIR ETag match — 304 for {} {}", request.getMethod(), request.getPath());
      return;
    }

    response.header("ETag", etag);
    response.header("Cache-Control", CACHE_CONTROL_REVALIDATING);
    clearNoStoreHeaders(response);
  }

  // -- Helpers ----------------------------------------------------------------

  /**
   * Read-shape: methods we're willing to attach an ETag to. {@code HEAD} carries the
   * same headers as {@code GET} (RFC 9110 §9.3.2), so it gets the same treatment.
   */
  private static boolean isReadShape(KefhirRequest request) {
    String method = request.getMethod();
    return "GET".equals(method) || "HEAD".equals(method);
  }

  /**
   * Serialise the response body and hash it. Returns {@code null} if the request's
   * preferred content type isn't installed as a {@link ResourceFormatService}
   * presenter — in which case kefhir's existing formatting flow will error out
   * downstream on the same lookup, and there's nothing useful for us to ETag.
   */
  private String computeWeakEtag(Object body, KefhirRequest request) {
    if (!(body instanceof Resource resource)) {
      // Non-Resource bodies (raw String, Parameters wrapped funky, …) — skip ETag
      // rather than guess at a serialisation.
      return null;
    }
    try {
      String format = pickResponseFormat(request);
      if (format == null) {
        return null; // No presenter matches the client's Accept; let kefhir's existing flow error.
      }
      ResourceContent formatted = resourceFormatService.compose(resource, format);
      byte[] bytes = formatted.getValue().getBytes(StandardCharsets.UTF_8);
      CRC32 crc = new CRC32();
      crc.update(bytes);
      return "W/\"" + Long.toHexString(crc.getValue()) + "-" + bytes.length + "\"";
    } catch (RuntimeException e) {
      // Defensive: ETag is an optimisation, not part of the response contract.
      // Don't fail the request because hashing failed.
      log.debug("FHIR ETag computation failed, leaving response untouched: {}", e.toString());
      return null;
    }
  }

  /**
   * Resolve the format kefhir will use to serialise the response body, so the bytes we
   * hash match the bytes the client receives. Mirrors {@code RuleThemAllFhirController.format}:
   * walk the request's {@code Accept} list against registered {@link ResourceRepresentation}
   * presenters and pick the first match.
   *
   * <p>Returns {@code null} when nothing matches — the caller skips the ETag and lets
   * kefhir's downstream content-type negotiation fail normally.
   */
  private String pickResponseFormat(KefhirRequest request) {
    List<MediaType> accept = request.getAccept();
    if (accept == null || accept.isEmpty()) {
      // Default to JSON when client didn't set Accept — matches kefhir's own fallback.
      return Optional.ofNullable(resourceFormatService.findPresenter("application/fhir+json").orElse(null))
          .or(() -> resourceFormatService.findPresenter("application/json"))
          .map(ResourceRepresentation::getName)
          .orElse(null);
    }
    List<String> wanted = accept.stream().map(MediaType::toString).toList();
    return resourceFormatService.findPresenter(wanted)
        .map(ResourceRepresentation::getName)
        .orElse(null);
  }

  /**
   * RFC 9110 §13.1.2 {@code If-None-Match} matching. Wildcard {@code *} matches any
   * current representation. Otherwise the header is a comma-separated list of
   * entity-tags; we match if any element equals the supplied tag. Weak/strong
   * distinction is irrelevant — this filter only emits weak ETags.
   */
  private static boolean matchesIfNoneMatch(String headerValue, String currentEtag) {
    if (headerValue == null || headerValue.isBlank()) {
      return false;
    }
    String trimmed = headerValue.trim();
    if (IF_NONE_MATCH_ANY.equals(trimmed)) {
      return true;
    }
    for (String candidate : trimmed.split(",")) {
      if (candidate.trim().equals(currentEtag)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Drop {@code Pragma} / {@code Expires} from the response headers. The kefhir
   * controller sets these defensively (HTTP/1.0 no-cache compatibility), but they
   * contradict the revalidating Cache-Control we just emitted, so we strip them.
   *
   * <p>{@link KefhirResponse#header(String, String)} <i>appends</i> to a list-valued
   * map — calling it with empty-string would produce two entries, not a removal. We
   * mutate the underlying map directly to get the remove-by-key semantics.
   */
  private static void clearNoStoreHeaders(KefhirResponse response) {
    if (response.getHeaders() != null) {
      response.getHeaders().remove("Pragma");
      response.getHeaders().remove("Expires");
    }
  }
}
