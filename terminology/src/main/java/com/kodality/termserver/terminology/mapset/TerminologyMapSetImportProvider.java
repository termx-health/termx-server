package com.kodality.termserver.terminology.mapset;

import com.kodality.termserver.ts.MapSetImportProvider;
import com.kodality.termserver.ts.mapset.MapSetTransactionRequest;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyMapSetImportProvider extends MapSetImportProvider {
  private final MapSetImportService importService;

  @Override
  public void importMapSet(MapSetTransactionRequest request) {
    importService.importMapSet(request);
  }
}
