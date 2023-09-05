package com.kodality.termx.terminology.mapset;

import com.kodality.termx.ts.MapSetImportProvider;
import com.kodality.termx.ts.mapset.MapSetTransactionRequest;
import javax.inject.Singleton;
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
