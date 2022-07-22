package com.kodality.termserver.snomed;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedImportRequest {
  private String refsetId;
}
