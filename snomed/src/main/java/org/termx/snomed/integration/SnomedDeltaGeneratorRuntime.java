package org.termx.snomed.integration;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * Subprocess wrapper for the IHTSDO {@code DeltaGeneratorTool} v3.0.0 jar bundled at
 * {@code snomed/delta-generator-tool/DeltaGeneratorTool-3.0.0.jar} (relative to the snomed
 * module's resources). Documented usage:
 *
 * <pre>java -Xms4G -Xmx4G -jar DeltaGeneratorTool.jar &lt;old.zip&gt; &lt;new.zip&gt; [--latest-state]</pre>
 *
 * The tool prints {@code Processing Complete. Rows exported: NNN} on stdout when it finishes
 * and writes the produced delta zip into the working directory. We invoke it out-of-process
 * for two reasons:
 *
 * <ol>
 *   <li><strong>Heap isolation.</strong> The tool's recommended 4 GB heap dwarfs the termx
 *       app server's reservation; running it in-JVM would force every other request to share
 *       that footprint for the entire scan.</li>
 *   <li><strong>The tool calls {@code System.exit}</strong> on validation errors. In-process
 *       invocation would kill the application JVM.</li>
 * </ol>
 *
 * The jar ships as a classpath resource (committed under {@code src/main/resources}) and is
 * extracted to a stable path under {@code java.io.tmpdir} on first call. Extraction is
 * idempotent — subsequent runs reuse the same file as long as the size matches.
 */
@Slf4j
@Singleton
public class SnomedDeltaGeneratorRuntime {
  /** Resource path inside the snomed module. Keep in sync with the file layout under {@code src/main/resources}. */
  private static final String JAR_RESOURCE = "/snomed/delta-generator-tool/DeltaGeneratorTool-3.0.0.jar";
  private static final String JAR_FILENAME = "DeltaGeneratorTool-3.0.0.jar";

  /** Recommended by upstream README — the tool eagerly grows tables when comparing full editions. */
  private static final String XMX = "-Xmx4G";
  private static final String XMS = "-Xms1G";

  private static final Pattern ROWS_EXPORTED = Pattern.compile("Rows exported:\\s*(\\d+)");

  private Path extractedJar;

  @PostConstruct
  void init() {
    try {
      this.extractedJar = ensureJarExtracted();
      log.info("DeltaGeneratorTool jar available at {}", extractedJar);
    } catch (IOException e) {
      log.warn("Failed to extract DeltaGeneratorTool jar at startup — will retry on first use: {}", e.getMessage());
    }
  }

  /**
   * Run the tool against two archives. Caller spools the Bob objects to {@code oldZip} /
   * {@code newZip} (typically temp files) and supplies a fresh {@code workDir} that the tool
   * will fill with intermediate files plus the produced delta zip.
   *
   * <p>The returned {@link Result} surfaces the delta zip path the caller should ingest, the
   * row count parsed from stdout, and the trailing log lines for surfacing in Lorque result
   * text.</p>
   */
  public Result run(Path oldZip, Path newZip, Path workDir, boolean latestState) throws IOException, InterruptedException {
    Path jar = (extractedJar != null) ? extractedJar : ensureJarExtracted();

    List<String> cmd = new ArrayList<>();
    cmd.add(javaBinary());
    cmd.add(XMS);
    cmd.add(XMX);
    cmd.add("-jar");
    cmd.add(jar.toAbsolutePath().toString());
    cmd.add(oldZip.toAbsolutePath().toString());
    cmd.add(newZip.toAbsolutePath().toString());
    if (latestState) {
      cmd.add("--latest-state");
    }

    log.info("Launching delta-generator: {}", String.join(" ", cmd));
    long started = System.currentTimeMillis();
    ProcessBuilder pb = new ProcessBuilder(cmd)
        .directory(workDir.toFile())
        .redirectErrorStream(true); // upstream tool intermixes stdout/stderr cleanly
    Process p = pb.start();

    StringBuilder log = new StringBuilder();
    Integer rowsExported = null;
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        log.append(line).append('\n');
        Matcher m = ROWS_EXPORTED.matcher(line);
        if (m.find()) {
          rowsExported = Integer.parseInt(m.group(1));
        }
      }
    }
    int exitCode = p.waitFor();
    long duration = System.currentTimeMillis() - started;
    SnomedDeltaGeneratorRuntime.log.info("delta-generator exit={} duration={}ms rowsExported={}", exitCode, duration, rowsExported);

    if (exitCode != 0) {
      throw new IOException("delta-generator-tool exited with code " + exitCode
          + ". Last log lines:\n" + tail(log.toString(), 40));
    }

    Path deltaZip = findGeneratedDeltaZip(workDir, oldZip, newZip)
        .orElseThrow(() -> new IOException("delta-generator-tool exited 0 but no delta zip found in " + workDir));

    return new Result()
        .setDeltaZip(deltaZip)
        .setRowsExported(rowsExported)
        .setDurationMs(duration)
        .setLogTail(tail(log.toString(), 40));
  }

  private synchronized Path ensureJarExtracted() throws IOException {
    Path dir = Path.of(System.getProperty("java.io.tmpdir"), "termx-delta-generator-tool");
    Files.createDirectories(dir);
    Path target = dir.resolve(JAR_FILENAME);

    long expectedSize = -1;
    try (InputStream in = SnomedDeltaGeneratorRuntime.class.getResourceAsStream(JAR_RESOURCE)) {
      if (in == null) {
        throw new IOException("DeltaGeneratorTool jar resource missing from classpath: " + JAR_RESOURCE);
      }
      // Stream into a temp file then atomic-rename so two concurrent extracts don't race.
      Path tmp = Files.createTempFile(dir, "extract-", ".jar.tmp");
      Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
      expectedSize = Files.size(tmp);
      if (Files.exists(target) && Files.size(target) == expectedSize) {
        // Already extracted at the right size — drop our copy
        Files.deleteIfExists(tmp);
      } else {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      }
    }
    return target;
  }

  /** Use {@code ${java.home}/bin/java} so the subprocess uses the same JDK we're running on,
   *  rather than whatever {@code PATH} resolves on the host (which is undefined in Docker). */
  private static String javaBinary() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  /**
   * The tool writes the delta into the CWD as a zip whose name starts with
   * {@code SnomedCT_RF2DELTA_}. There may be other transient artifacts; we pick the largest
   * zip whose name isn't one of our inputs. (Belt-and-braces: also accept any .zip if the
   * SnomedCT prefix isn't present.)
   */
  private static java.util.Optional<Path> findGeneratedDeltaZip(Path workDir, Path oldZip, Path newZip) throws IOException {
    String inputOld = oldZip.getFileName().toString();
    String inputNew = newZip.getFileName().toString();
    try (var s = Files.list(workDir)) {
      return s
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))
          .filter(p -> !p.getFileName().toString().equals(inputOld))
          .filter(p -> !p.getFileName().toString().equals(inputNew))
          .sorted((a, b) -> Long.compare(sizeOrZero(b), sizeOrZero(a)))
          .findFirst();
    }
  }

  private static long sizeOrZero(Path p) {
    try {
      return Files.size(p);
    } catch (IOException e) {
      return 0L;
    }
  }

  private static String tail(String s, int maxLines) {
    if (s == null || s.isEmpty()) {
      return "";
    }
    String[] lines = s.split("\n");
    int from = Math.max(0, lines.length - maxLines);
    StringBuilder sb = new StringBuilder();
    for (int i = from; i < lines.length; i++) {
      sb.append(lines[i]).append('\n');
    }
    return sb.toString();
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class Result {
    /** Absolute path to the produced delta zip inside the work dir. */
    private Path deltaZip;
    /** Parsed from the tool's "Rows exported: N" stdout line; {@code null} if not seen. */
    private Integer rowsExported;
    private long durationMs;
    /** Last ~40 lines of subprocess output — kept short for Lorque result-text storage. */
    private String logTail;
  }
}
