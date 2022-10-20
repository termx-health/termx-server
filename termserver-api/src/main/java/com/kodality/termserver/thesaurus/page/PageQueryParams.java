package com.kodality.termserver.thesaurus.page;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageQueryParams extends QueryParams {
  private boolean root;
  private Long idNe;
  private Long rootId;
  private String textContains;
  private String slug;
}
