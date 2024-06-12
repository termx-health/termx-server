package com.kodality.termx.core.ts;

import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetVersion;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class MapSetProvider {
  public abstract Optional<MapSet> loadMapSet(String mapSet);
  public abstract Optional<MapSetVersion> loadMapSetVersion(String mapSet, String version);
  public abstract Optional<MapSetVersion> loadPreviousMapSetVersion(String mapSet, String version);
  public abstract void activateVersion(String mapSet, String version);
}
