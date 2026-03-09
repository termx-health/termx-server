package org.termx.core.ts;

import org.termx.ts.mapset.MapSetTransactionRequest;

public abstract class MapSetImportProvider {

  public abstract void importMapSet(MapSetTransactionRequest request);
}
