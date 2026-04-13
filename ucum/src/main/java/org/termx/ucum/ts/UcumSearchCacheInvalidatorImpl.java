package org.termx.ucum.ts;

import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.termx.core.ts.UcumSearchCacheInvalidator;

@Singleton
@RequiredArgsConstructor
public class UcumSearchCacheInvalidatorImpl implements UcumSearchCacheInvalidator {
  private final UcumConceptResolver ucumConceptResolver;

  @Override
  public void invalidate() {
    ucumConceptResolver.invalidateCache();
  }
}
