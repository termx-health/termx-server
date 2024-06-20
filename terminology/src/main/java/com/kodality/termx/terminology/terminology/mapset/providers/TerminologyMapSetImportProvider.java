package com.kodality.termx.terminology.terminology.mapset.providers;

import com.kodality.termx.core.ts.MapSetImportProvider;
import com.kodality.termx.terminology.terminology.mapset.MapSetService;
import com.kodality.termx.ts.mapset.MapSetTransactionRequest;
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
