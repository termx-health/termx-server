package com.kodality.termx.core.ts;

import com.kodality.termx.ts.codesystem.CodeSystemImportRequest;

public abstract class CodeSystemImportProvider {

  public abstract void importCodeSystem(CodeSystemImportRequest request);
}
