package org.termx.terminology.fhir.codesystem

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Architectural regression guard for the inline-entity cap added in PR #157.
 *
 * <p>The cap is the only thing between {@code GET /fhir/CodeSystem/{id}} and a JDBC-buffered
 * 370k-row result set that OOMs dev-server. {@link CodeSystemResourceStorageTest} verifies
 * the runtime behaviour with mocks; this spec verifies that the structural ingredients of
 * the cap are *still in the file* so a future refactor can't silently delete them.
 *
 * <p>Crude (it greps the source file), but cheap and unambiguous — if it fails, you've
 * removed the cap. ArchUnit would be a better fit for a broad rule, but the bug here is
 * narrow and per-method, which ArchUnit's class-level analysis can't express well.
 *
 * <p>If you intentionally rewrite the cap (e.g. promote it to a streaming implementation),
 * update this spec to match the new ingredients.
 */
class CodeSystemReadCapPresenceTest extends Specification {
  static final Path CS_STORAGE = Paths.get(
      "src/main/java/org/termx/terminology/fhir/codesystem/CodeSystemResourceStorage.java")

  def "CodeSystemResourceStorage retains the maxInlineEntities cap"() {
    given:
    String src = Files.readString(CS_STORAGE)

    expect:
    // The configurable knob exists and is reachable from compose
    src.contains("maxInlineEntities")
    src.contains("termx.fhir.codesystem.read.max-inline-entities")

    // The actual gate condition is still in loadEntities
    src ==~ /(?s).*total\s*>\s*maxInlineEntities.*/

    // A WARN-level log fires when the gate engages, so operators see which CS triggered
    src.contains("log.warn")
    src.contains("exceeding inline cap")
  }

  def "the cap default is large enough for typical CodeSystems but smaller than SNOMED CT"() {
    given:
    String src = Files.readString(CS_STORAGE)

    expect:
    // The default is the number after the colon in the @Value("${...:DEFAULT}") annotation.
    // Must be > 1000 (the search-path threshold) and < 370_000 (SNOMED CT size).
    def matcher = src =~ /max-inline-entities:(\d+)/
    matcher.find()
    int defaultCap = matcher.group(1) as int
    defaultCap > 1000
    defaultCap < 370_000
  }
}
