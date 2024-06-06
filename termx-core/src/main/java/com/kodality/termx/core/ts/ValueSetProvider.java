package com.kodality.termx.core.ts;

import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetCompareResult;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@RequiredArgsConstructor
public abstract class ValueSetProvider {
  public abstract Optional<ValueSetVersion> loadValueSetVersion(String valueSet, String version);
  public abstract void activateVersion(String valueSet, String version);
  public abstract Pair<ValueSet, ValueSetCompareResult> compareWithPreviousVersion(String valueSet, String version);
}
