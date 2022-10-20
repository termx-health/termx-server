package com.kodality.termserver.thesaurus.page;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageContentQueryParams extends QueryParams {
  private String slug;
}
