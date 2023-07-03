package com.kodality.termx.fileimporter.association.utils;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class AssociationFileImportRow {
  private String source;
  private Long sourceConceptId; // decorated
  private String target;
  private Long targetConceptId; // decorated
  private Integer order;
}
