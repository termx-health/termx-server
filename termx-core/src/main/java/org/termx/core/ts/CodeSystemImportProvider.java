package org.termx.core.ts;

import org.termx.ts.codesystem.CodeSystemImportRequest;

public abstract class CodeSystemImportProvider {

  public abstract void importCodeSystem(CodeSystemImportRequest request);
}
