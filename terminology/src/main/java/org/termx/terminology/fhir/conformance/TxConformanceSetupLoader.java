package org.termx.terminology.fhir.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the HL7 tx-ecosystem test fixtures (the {@code http://hl7.org/fhir/test/…} CodeSystems and
 * ValueSets the suites expand/validate against) into the server under test, before a conformance run.
 *
 * <p>The HL7 test runner assumes the server already hosts this content (tx.fhir.org does); an empty
 * termx instead returns "not found" for every test. This loader closes that gap so a run is
 * self-contained. It loads ALL setup resources (the "server hosts the content" model), reading them
 * from the FHIR validator's package cache — files named {@code codesystem-*.json} / {@code valueset-*.json}
 * (the {@code *-request*}/{@code *-response*} files are test artifacts, not content).
 *
 * <p>CodeSystems are sent with {@code PUT} (idempotent upsert — termx enables CodeSystem update);
 * ValueSets with {@code POST} (termx only enables ValueSet create) — duplicates on re-run are
 * tolerated. Per-resource failures are logged, not fatal.
 *
 * <p>NB: it does NOT raise the pass rate by itself — it only makes the content present; genuine
 * termx FHIR conformance gaps still fail. It also writes test content into the target server, so it
 * is opt-in (the {@code loadSetup} flag) and meant for dedicated conformance instances.
 */
@Slf4j
@Singleton
public class TxConformanceSetupLoader {
  private static final String FHIR_JSON = "application/fhir+json";

  private final String packageDir;
  // Writing setup content requires CodeSystem/ValueSet create+update privileges, but the loader calls the
  // FHIR endpoint over HTTP — where, unauthenticated, it is a guest and every write is 403 Forbidden. A
  // bearer token (the dev token locally, a service token on a dedicated conformance instance) authorizes it.
  private final String authToken;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public TxConformanceSetupLoader(@Value("${termx.conformance.test-package-dir:}") String packageDir,
                                  @Value("${termx.conformance.setup-auth-token:}") String authToken) {
    // Default to the FHIR validator's package cache, where txTests downloads the tx-ecosystem fixtures.
    this.packageDir = StringUtils.isNotEmpty(packageDir) ? packageDir
        : System.getProperty("user.home") + "/.fhir/packages/hl7.fhir.uv.tx-ecosystem#current/package/tests";
    this.authToken = authToken;
  }

  /** Loads every setup CodeSystem then ValueSet under the package dir into {@code fhirBaseUrl}. */
  public void load(String fhirBaseUrl) {
    Path root = Path.of(packageDir);
    if (!Files.isDirectory(root)) {
      throw new IllegalStateException("tx-ecosystem test package not found at " + packageDir
          + " — run the suite once (the validator downloads it) or set termx.conformance.test-package-dir");
    }
    List<Path> codeSystems = findSetupFiles(root, "CodeSystem");
    List<Path> valueSets = findSetupFiles(root, "ValueSet");
    int total = codeSystems.size() + valueSets.size();
    log.info("tx conformance setup: loading {} CodeSystems and {} ValueSets into {}", codeSystems.size(), valueSets.size(), fhirBaseUrl);

    // Dependency order is not knowable up front: supplement/derived CodeSystems reference a base
    // CodeSystem, and ValueSets reference CodeSystems and other ValueSets. Rather than topologically
    // sort, load in repeated passes until a pass resolves nothing new (fixpoint). Each remaining resource
    // is retried while progress is still being made, so multi-level chains (A←B←C) resolve over passes.
    java.util.LinkedHashMap<Path, Boolean> remaining = new java.util.LinkedHashMap<>();
    codeSystems.forEach(p -> remaining.put(p, true));
    valueSets.forEach(p -> remaining.put(p, false));
    int ok = 0;
    int pass = 0;
    boolean progressed = true;
    while (progressed && !remaining.isEmpty()) {
      progressed = false;
      pass++;
      for (java.util.Iterator<java.util.Map.Entry<Path, Boolean>> it = remaining.entrySet().iterator(); it.hasNext(); ) {
        java.util.Map.Entry<Path, Boolean> e = it.next();
        boolean isCs = e.getValue();
        String url = isCs ? fhirBaseUrl + "/CodeSystem/" + idOf(e.getKey()) : fhirBaseUrl + "/ValueSet";
        if (send(url, isCs ? "PUT" : "POST", e.getKey())) {
          it.remove();
          ok++;
          progressed = true;
        }
      }
      log.info("tx conformance setup: pass {} — {}/{} loaded, {} still pending", pass, ok, total, remaining.size());
    }
    log.info("tx conformance setup: loaded {} of {} resources in {} pass(es) ({} unresolved)", ok, total, pass, remaining.size());
  }

  /**
   * Setup resources of the given FHIR {@code resourceType} ("CodeSystem"/"ValueSet"), classified by the
   * resource's own {@code resourceType} rather than its filename — the tx-ecosystem ships content under
   * non-{@code codesystem-}/{@code valueset-} names too (e.g. {@code cs1.json} = simple1,
   * {@code exclude-expand-valueSet.json}), which a filename-prefix filter silently dropped. Test
   * {@code *-request*}/{@code *-response*} artifacts are excluded.
   */
  static List<Path> findSetupFiles(Path root, String resourceType) {
    try (Stream<Path> s = Files.walk(root)) {
      return s.filter(Files::isRegularFile)
          .filter(p -> isSetupFile(p.getFileName().toString()))
          .filter(p -> resourceType.equals(resourceTypeOf(p)))
          // Canonical resources (FHIR id == url's last segment) FIRST, so they claim their natural id before
          // a resource that reuses that id under a different url. The tx-ecosystem ships
          // codesystem-overload-1.json with id "simple" but url ".../overload"; loaded before the real
          // codesystem-simple.json it would hijack the "simple" code system and pollute it with phantom
          // versions. termx keys on the resource id, so order matters; this makes it deterministic.
          .sorted(Comparator.comparingInt(TxConformanceSetupLoader::canonicalRank).thenComparing(Path::toString))
          .toList();
    } catch (Exception e) {
      throw new RuntimeException("failed scanning test package " + root, e);
    }
  }

  /** 0 when the resource's FHIR id matches its url's last segment (a "canonical" resource), 1 otherwise. */
  static int canonicalRank(Path p) {
    try {
      JsonNode r = JsonUtil.getObjectMapper().readTree(p.toFile());
      String id = r.path("id").asText("");
      String url = r.path("url").asText("");
      if (id.isEmpty() || url.isEmpty()) {
        return 0;
      }
      String last = url.contains("/") ? url.substring(url.lastIndexOf('/') + 1) : url;
      return id.equals(last) ? 0 : 1;
    } catch (Exception e) {
      return 0;
    }
  }

  /** A candidate setup file: a {@code .json} that is not a test request/response artifact. */
  static boolean isSetupFile(String name) {
    String n = name.toLowerCase();
    return n.endsWith(".json") && !n.contains("-request") && !n.contains("-response");
  }

  /** The FHIR {@code resourceType} declared by a JSON fixture, or {@code ""} if unreadable / not a resource. */
  static String resourceTypeOf(Path p) {
    try {
      return JsonUtil.getObjectMapper().readTree(p.toFile()).path("resourceType").asText("");
    } catch (Exception e) {
      return "";
    }
  }

  private static String idOf(Path p) {
    String n = p.getFileName().toString();
    return n.substring(0, n.length() - ".json".length());
  }

  private boolean send(String url, String method, Path body) {
    try {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(60))
          .header("Content-Type", FHIR_JSON)
          .header("Accept", FHIR_JSON);
      if (StringUtils.isNotEmpty(authToken)) {
        b.header("Authorization", "Bearer " + authToken);
      }
      HttpRequest req = b.method(method, BodyPublishers.ofString(sanitize(body))).build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 == 2) {
        return true;
      }
      // The tx-ecosystem ships the same canonical (url) at the same version more than once — e.g. a value
      // set referenced by several suites, or duplicated across test folders. reconcileCanonicalId folds
      // these to one resource, so the second POST of an identical, already-active version is rejected
      // (TE104 "already final" / TE102 "already exists"). Since the import is transactional, an active
      // version means a complete prior load — the content is present either way, so treat it as loaded
      // (matching this loader's "duplicates on re-run are tolerated" contract) rather than re-posting it
      // every pass and reporting it as unresolved.
      if (alreadyPresent(resp.body())) {
        log.info("tx conformance setup: {} {} -> already present, treating as loaded", method, url);
        return true;
      }
      // Honest logging: report the real status + a snippet of the error so an incomplete setup is
      // diagnosable, instead of the misleading blanket "already present".
      log.info("tx conformance setup: {} {} -> {}: {}", method, url, resp.statusCode(), summarize(resp.body()));
      return false;
    } catch (Exception e) {
      log.warn("tx conformance setup: {} {} failed: {}", method, url, e.getMessage());
      return false;
    }
  }

  /**
   * Reads the fixture and strips metadata termx's resource validation rejects but that has no bearing on
   * the terminology operations under test — currently {@code versionAlgorithm[x]} (a version-comparison
   * hint that references code systems termx does not host, failing the whole import on an otherwise valid
   * resource). Returns the original bytes verbatim if anything goes wrong, so stripping never blocks a load.
   */
  static String sanitize(Path body) {
    try {
      JsonNode root = JsonUtil.getObjectMapper().readTree(body.toFile());
      if (root instanceof ObjectNode obj) {
        obj.remove("versionAlgorithmString");
        obj.remove("versionAlgorithmCoding");
      }
      return JsonUtil.getObjectMapper().writeValueAsString(root);
    } catch (java.io.IOException e) {
      try {
        return Files.readString(body);
      } catch (java.io.IOException ignored) {
        return "";
      }
    }
  }

  /**
   * True when an import failure means the resource is already present (a duplicate of an already-loaded,
   * activated canonical): TE104 "version already final" or TE102 "version already exists". Such a resource's
   * content is in the server, so the loader treats it as loaded rather than retrying it every pass.
   */
  static boolean alreadyPresent(String responseBody) {
    if (responseBody == null) {
      return false;
    }
    return responseBody.contains("TE104") || responseBody.contains("TE102");
  }

  private static String summarize(String responseBody) {
    if (responseBody == null) {
      return "";
    }
    String b = responseBody.replaceAll("\\s+", " ").trim();
    return b.length() > 200 ? b.substring(0, 200) + "…" : b;
  }
}
