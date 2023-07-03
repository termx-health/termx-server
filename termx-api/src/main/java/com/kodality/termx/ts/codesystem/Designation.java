package com.kodality.termx.ts.codesystem;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class Designation {
  private Long id;
  private Long designationTypeId;
  private String name;
  private String language;
  private String rendering;
  private boolean preferred;
  private String caseSignificance;
  private String designationKind; //text, blob
  private String description;
  private String status;
  private Long codeSystemEntityVersionId;

  private Long supplementId;

  private String designationType;
}
