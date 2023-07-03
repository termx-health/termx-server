package com.kodality.termx.snomed.decriptionitem;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedDescriptionItemSearchParams {
  private String term;
  private Boolean active;
  private String language;
  private String semanticTags;
  private Boolean conceptActive;
  private Boolean groupByConcept;
  private Long limit;
}
