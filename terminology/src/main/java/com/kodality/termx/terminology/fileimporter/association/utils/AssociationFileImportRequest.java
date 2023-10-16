package com.kodality.termx.terminology.fileimporter.association.utils;

import io.micronaut.core.annotation.Introspected;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class AssociationFileImportRequest {
  @NotNull
  private String codeSystemId;
  @NotNull
  private Long codeSystemVersionId;
  @NotNull
  private String associationType;

  private String sourceColumn;
  private String sourceColumnSeparator;
  private String targetColumn;
  private String orderColumn;
}
