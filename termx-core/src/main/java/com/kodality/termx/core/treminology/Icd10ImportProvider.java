package com.kodality.termx.core.treminology;

public abstract class Icd10ImportProvider {
  public abstract void importIcd10(String system, byte[] file);

}
