package com.kodality.termx.core.ts;

import com.kodality.termx.ts.valueset.ValueSetVersion;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class ValueSetProvider {
  public abstract Optional<ValueSetVersion> loadValueSetVersion(String valueSet, String version);
  public abstract void activateVersion(String valueSet, String version);
}
