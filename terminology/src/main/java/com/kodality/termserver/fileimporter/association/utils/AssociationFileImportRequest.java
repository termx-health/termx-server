package com.kodality.termserver.fileimporter.association.utils;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class AssociationFileImportRequest {
  private String codeSystemId;
  private Long codeSystemVersionId;
  private String associationType;

  private String sourceColumn;
  private String sourceColumnSeparator;
  private String targetColumn;
  private String orderColumn;
}
