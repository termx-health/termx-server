package org.termx.terminology.mapset

import com.kodality.commons.model.LocalizedName
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.terminology.mapset.MapSetService
import org.termx.terminology.terminology.mapset.version.MapSetVersionService
import org.termx.ts.PublicationStatus
import org.termx.ts.mapset.MapSet
import org.termx.ts.mapset.MapSetVersion

import java.time.LocalDate

/**
 * RED test for tehik ticket KL-98 — {@code MapSetVersionRepository.loadPreviousVersion} must return the prior
 * version even when it shares a {@code release_date} with the current version.
 *
 * <p>The buggy base query excludes same-dated versions with a strict {@code release_date < current} predicate, so
 * {@code loadPrevious("msprev", "2.0.0")} returns null when 1.0.0 and 2.0.0 share a release date. The fix relaxes
 * the comparison to {@code <=}, excludes the current version by {@code version}, and orders by version desc.
 */
@MicronautTest(transactional = true)
class MapSetPreviousVersionIT extends TermxIntegTest {
  @Inject MapSetService mapSetService
  @Inject MapSetVersionService versionService

  void setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "loadPrevious resolves the prior version even when it shares a release date"() {
    given: "a map set with two draft versions 1.0.0 and 2.0.0 sharing the same release date"
    MapSet mapSet = new MapSet().setId("msprev")
    mapSet.setUri("http://termx-test.local/MapSet/msprev")
    mapSet.setName("msprev")
    mapSet.setTitle(new LocalizedName().add("en", "Prev MapSet"))
    mapSetService.save(mapSet)

    LocalDate releaseDate = LocalDate.of(2024, 1, 1)
    saveVersion("1.0.0", releaseDate)
    saveVersion("2.0.0", releaseDate)

    expect: "the previous version of 2.0.0 is 1.0.0"
    versionService.loadPrevious("msprev", "2.0.0").map { it.version }.orElse(null) == "1.0.0"
  }

  private void saveVersion(String version, LocalDate releaseDate) {
    MapSetVersion v = new MapSetVersion()
    v.setMapSet("msprev")
    v.setVersion(version)
    v.setStatus(PublicationStatus.draft)
    v.setReleaseDate(releaseDate)
    // scope must serialize to a non-empty jsonb: core.jsonb_trunc collapses an all-null object to SQL NULL,
    // which violates map_set_version.scope NOT NULL. Set a scalar field so the object survives truncation.
    MapSetVersion.MapSetVersionScope scope = new MapSetVersion.MapSetVersionScope()
    scope.setSourceType("code-system")
    scope.setTargetType("code-system")
    v.setScope(scope)
    versionService.save(v)
  }
}
