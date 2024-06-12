package com.kodality.termx.terminology.terminology.mapset.providers;

import com.kodality.termx.core.ts.MapSetProvider;
import com.kodality.termx.terminology.terminology.mapset.MapSetService;
import com.kodality.termx.terminology.terminology.mapset.version.MapSetVersionService;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetVersion;
import java.util.Optional;
import javax.inject.Singleton;
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
