package com.kodality.termx.fileimporter.valueset;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class ValueSetFileImportRequest {
  private String valueSetId;
  private String type; // json; fsh
}
