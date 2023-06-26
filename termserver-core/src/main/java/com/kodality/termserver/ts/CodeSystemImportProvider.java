package com.kodality.termserver.ts;

import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest;

public abstract class CodeSystemImportProvider {

  public abstract void importCodeSystem(CodeSystemImportRequest request);
}
