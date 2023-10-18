package com.kodality.termx.snomed.concept;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedTranslation {
  private Long id;
  private String descriptionId;
  private String conceptId;
  private String module;
  private String branch;
  private String language;
  private String term;
  private String type;
  private String acceptability;
  private String status;
}
