package com.kodality.termserver.loinc.utils;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoincImportRequest {
  private String version;
  private String language;
}
