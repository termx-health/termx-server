package com.kodality.termx.ts.mapset;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MapSetAutomapRequest {
  private boolean mapByCode;
  private boolean mapByDesignation;
  private String sourceProperty;
  private String sourceLanguage;
  private String targetProperty;
  private String targetLanguage;
}
