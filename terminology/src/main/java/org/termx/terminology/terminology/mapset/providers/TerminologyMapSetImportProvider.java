package org.termx.terminology.terminology.mapset.providers;

import org.termx.core.ts.MapSetImportProvider;
import org.termx.terminology.terminology.mapset.MapSetService;
import org.termx.ts.mapset.MapSetTransactionRequest;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyMapSetImportProvider extends MapSetImportProvider {
  private final MapSetService mapSetService;

  @Override
  public void importMapSet(MapSetTransactionRequest request) {
    mapSetService.save(request);
  }
}
