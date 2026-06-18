package org.termx.terminology.fhir.conformance;

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
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public TxConformanceSetupLoader(@Value("${termx.conformance.test-package-dir:}") String packageDir) {
    // Default to the FHIR validator's package cache, where txTests downloads the tx-ecosystem fixtures.
    this.packageDir = StringUtils.isNotEmpty(packageDir) ? packageDir
        : System.getProperty("user.home") + "/.fhir/packages/hl7.fhir.uv.tx-ecosystem#current/package/tests";
  }

  /** Loads every setup CodeSystem then ValueSet under the package dir into {@code fhirBaseUrl}. */
  public void load(String fhirBaseUrl) {
    Path root = Path.of(packageDir);
    if (!Files.isDirectory(root)) {
      throw new IllegalStateException("tx-ecosystem test package not found at " + packageDir
          + " — run the suite once (the validator downloads it) or set termx.conformance.test-package-dir");
    }
    List<Path> codeSystems = findSetupFiles(root, "codesystem-");
    List<Path> valueSets = findSetupFiles(root, "valueset-");
    log.info("tx conformance setup: loading {} CodeSystems and {} ValueSets into {}", codeSystems.size(), valueSets.size(), fhirBaseUrl);

    int ok = 0;
    int failed = 0;
    for (Path p : codeSystems) {
      if (send(fhirBaseUrl + "/CodeSystem/" + idOf(p), "PUT", p)) { ok++; } else { failed++; }
    }
    for (Path p : valueSets) {
      if (send(fhirBaseUrl + "/ValueSet", "POST", p)) { ok++; } else { failed++; }
    }
    log.info("tx conformance setup: loaded {} resources ({} failed/duplicate)", ok, failed);
  }

  /** Setup resources: {@code codesystem-*.json} / {@code valueset-*.json}, excluding test request/response artifacts. */
  static List<Path> findSetupFiles(Path root, String prefix) {
    try (Stream<Path> s = Files.walk(root)) {
      return s.filter(Files::isRegularFile)
          .filter(p -> isSetupFile(p.getFileName().toString(), prefix))
          .sorted(Comparator.comparing(Path::toString))
          .toList();
    } catch (Exception e) {
      throw new RuntimeException("failed scanning test package " + root, e);
    }
  }

  /** True for a setup resource file of the given kind (prefix); false for request/response test files. */
  static boolean isSetupFile(String name, String prefix) {
    String n = name.toLowerCase();
    return n.startsWith(prefix) && n.endsWith(".json") && !n.contains("-request") && !n.contains("-response");
  }

  private static String idOf(Path p) {
    String n = p.getFileName().toString();
    return n.substring(0, n.length() - ".json".length());
  }

  private boolean send(String url, String method, Path body) {
    try {
      HttpRequest req = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(60))
          .header("Content-Type", FHIR_JSON)
          .header("Accept", FHIR_JSON)
          .method(method, BodyPublishers.ofFile(body))
          .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 == 2) {
        return true;
      }
      log.debug("tx conformance setup: {} {} -> {} (likely already present)", method, url, resp.statusCode());
      return false;
    } catch (Exception e) {
      log.warn("tx conformance setup: {} {} failed: {}", method, url, e.getMessage());
      return false;
    }
  }
}
