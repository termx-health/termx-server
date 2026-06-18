package org.termx.terminology.fhir.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.termx.ts.conformance.TxConformanceRunRequest;

/**
 * Runs the HL7 FHIR terminology ("tx-ecosystem") test suite against a terminology server by shelling
 * out to the FHIR validator's {@code txTests} command, mirroring {@code SnomedDeltaGeneratorRuntime}'s
 * subprocess pattern.
 *
 * <pre>java -jar &lt;validator&gt; txTests -tx &lt;server&gt;/fhir -output &lt;tmp&gt; [-suite ..][-filter ..][-mode ..][-input ..]</pre>
 *
 * <p>The validator (~180MB) is NOT bundled in the repo; its path is configured via
 * {@code termx.conformance.validator-jar} (the deployment provides it). The bundled tx-ecosystem
 * test cases are loaded by the validator itself — nothing is vendored here.
 *
 * <p>The command's exit code reflects test pass/fail, so it is NOT treated as an error; the produced
 * {@code report.json} (a FHIR {@code TestReport}) is the result, and only a missing report is fatal.
 */
@Slf4j
@Singleton
public class TxConformanceRunner {
  private static final String XMX = "-Xmx2G";

  private final String validatorJar;
  private final String txUrl;

  public TxConformanceRunner(
      @Value("${termx.conformance.validator-jar:}") String validatorJar,
      @Value("${termx.conformance.tx-url:http://localhost:8200/fhir}") String txUrl) {
    this.validatorJar = validatorJar;
    this.txUrl = txUrl;
  }

  /** The FHIR base URL this server is tested at ({@code -tx}); also the target for setup loading. */
  public String getTxUrl() {
    return txUrl;
  }

  /** Runs the suite and returns the raw {@code report.json} (a FHIR {@code TestReport}). */
  public String run(TxConformanceRunRequest req, Path inputBundle) throws IOException, InterruptedException {
    if (StringUtils.isEmpty(validatorJar)) {
      throw new IllegalStateException("termx.conformance.validator-jar is not configured");
    }
    Path jar = Path.of(validatorJar);
    if (!Files.exists(jar)) {
      throw new IllegalStateException("validator jar not found at " + validatorJar);
    }
    Path output = Files.createTempDirectory("termx-txtests-");
    List<String> cmd = buildCommand(jar, output, req, inputBundle);
    log.info("Launching tx conformance tests: {}", String.join(" ", cmd));

    long started = System.currentTimeMillis();
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    StringBuilder out = new StringBuilder();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        out.append(line).append('\n');
      }
    }
    int exit = p.waitFor();
    long durationMs = System.currentTimeMillis() - started;

    Path reportFile = output.resolve("report.json");
    if (!Files.exists(reportFile)) {
      throw new IOException("tx conformance run produced no report.json (exit " + exit + "). Log tail:\n" + tail(out.toString(), 40));
    }
    String report = Files.readString(reportFile);
    log.info("tx conformance run finished in {} ms (exit {}): {}", durationMs, exit, summarize(report));
    return report;
  }

  /** Builds the validator command line. Package-private for unit testing. */
  List<String> buildCommand(Path jar, Path output, TxConformanceRunRequest req, Path inputBundle) {
    List<String> cmd = new ArrayList<>();
    cmd.add(javaBinary());
    cmd.add(XMX);
    cmd.add("-jar");
    cmd.add(jar.toAbsolutePath().toString());
    cmd.add("txTests");
    cmd.add("-tx");
    cmd.add(txUrl);
    cmd.add("-output");
    cmd.add(output.toAbsolutePath().toString());
    if (req != null) {
      if (StringUtils.isNotEmpty(req.getSuite())) {
        cmd.add("-suite");
        cmd.add(req.getSuite());
      }
      if (StringUtils.isNotEmpty(req.getFilter())) {
        cmd.add("-filter");
        cmd.add(req.getFilter());
      }
      if (StringUtils.isNotEmpty(req.getMode())) {
        cmd.add("-mode");
        cmd.add(req.getMode());
      }
    }
    if (inputBundle != null) {
      cmd.add("-input");
      cmd.add(inputBundle.toAbsolutePath().toString());
    }
    return cmd;
  }

  /** One-line pass/fail summary from a FHIR TestReport. Package-private for unit testing. */
  static String summarize(String reportJson) {
    try {
      JsonNode n = JsonUtil.getObjectMapper().readTree(reportJson);
      int passed = 0;
      int failed = 0;
      JsonNode tests = n.get("test");
      if (tests != null && tests.isArray()) {
        for (JsonNode t : tests) {
          JsonNode actions = t.get("action");
          String result = null;
          if (actions != null && actions.isArray() && !actions.isEmpty()) {
            JsonNode op = actions.get(0).get("operation");
            if (op != null && op.get("result") != null) {
              result = op.get("result").asText();
            }
          }
          if ("pass".equals(result)) {
            passed++;
          } else {
            failed++;
          }
        }
      }
      String result = n.path("result").asText("");
      double score = n.path("score").asDouble(0);
      return String.format("result=%s score=%.3f passed=%d failed=%d", result, score, passed, failed);
    } catch (Exception e) {
      return "unparseable report: " + e.getMessage();
    }
  }

  private static String javaBinary() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  private static String tail(String s, int lines) {
    String[] arr = s.split("\n");
    int from = Math.max(0, arr.length - lines);
    return String.join("\n", List.of(arr).subList(from, arr.length));
  }
}
