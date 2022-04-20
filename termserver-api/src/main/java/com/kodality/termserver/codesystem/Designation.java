package com.kodality.termserver.codesystem;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
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
}
