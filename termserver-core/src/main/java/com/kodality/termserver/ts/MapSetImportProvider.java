package com.kodality.termserver.ts;

import com.kodality.termserver.ts.mapset.MapSetTransactionRequest;

public abstract class MapSetImportProvider {

  public abstract void importMapSet(MapSetTransactionRequest request);
}
