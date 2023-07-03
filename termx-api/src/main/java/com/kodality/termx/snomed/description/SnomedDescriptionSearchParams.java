package com.kodality.termx.snomed.description;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedDescriptionSearchParams extends QueryParams {
  private String conceptId;
  private List<String> conceptIds;

  private String searchAfter;

  private boolean all;
}
