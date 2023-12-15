package com.kodality.termx.snomed.refset;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedRefsetSearchParams extends QueryParams {
  private String referenceSet;
  private Boolean active;
  private String referencedComponentId;

  private String branch;
}
