package com.kodality.termserver.fileimporter.valueset;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class ValueSetFileImportRequest {
  private String link;
  private String type; // json; fsh
}
