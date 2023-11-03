package com.kodality.termx.editionint.loinc.utils;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class LoincImportRequest {
  private String version;
  private String language;
}
