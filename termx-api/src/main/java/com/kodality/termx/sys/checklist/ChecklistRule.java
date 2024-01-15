package com.kodality.termx.sys.checklist;

import com.kodality.commons.model.LocalizedName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ChecklistRule {
  private Long id;
  private String code;
  private LocalizedName title;
  private LocalizedName description;
  private boolean active;
  private String type;
  private String verification;
  private String severity;
  private String target;
  private String resourceType;
}
