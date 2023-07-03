package com.kodality.termx.snomed.concept;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedTranslationSearchParams extends QueryParams {
  private String descriptionIds;
  private String status;
}
