package com.kodality.termx.core.ts;

import com.kodality.termx.ts.mapset.MapSetTransactionRequest;

public abstract class MapSetImportProvider {

  public abstract void importMapSet(MapSetTransactionRequest request);
}
