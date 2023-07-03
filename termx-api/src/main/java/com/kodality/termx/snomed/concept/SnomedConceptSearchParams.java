package com.kodality.termx.snomed.concept;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedConceptSearchParams extends QueryParams {
  private List<String> conceptIds;
  private String term;
  private Boolean termActive;
  private String language;
  private String ecl;
  private String semanticTags;
  private Boolean groupByConcept;
  private Boolean active;
  private Boolean conceptActive;

  private String searchAfter;

  private boolean all;
}
