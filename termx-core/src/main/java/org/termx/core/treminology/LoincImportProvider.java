package org.termx.core.treminology;

public abstract class LoincImportProvider {
  public abstract void importLoinc(String system, byte[] file);
}
