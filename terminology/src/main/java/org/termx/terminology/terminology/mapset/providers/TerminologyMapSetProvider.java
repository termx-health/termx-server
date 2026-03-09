package org.termx.terminology.terminology.mapset.providers;

import org.termx.core.ts.MapSetProvider;
import org.termx.terminology.terminology.mapset.MapSetService;
import org.termx.terminology.terminology.mapset.version.MapSetVersionService;
import org.termx.ts.mapset.MapSet;
import org.termx.ts.mapset.MapSetVersion;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyMapSetProvider extends MapSetProvider {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;

  @Override
  public Optional<MapSet> loadMapSet(String mapSet) {
    return mapSetService.load(mapSet);
  }

  @Override
  public Optional<MapSetVersion> loadMapSetVersion(String mapSet, String version) {
    return mapSetVersionService.load(mapSet, version);
  }

  @Override
  public Optional<MapSetVersion> loadPreviousMapSetVersion(String mapSet, String version) {
    return mapSetVersionService.loadPrevious(mapSet, version);
  }

  @Override
  public void activateVersion(String mapSet, String version) {
    mapSetVersionService.activate(mapSet, version);
  }
}
