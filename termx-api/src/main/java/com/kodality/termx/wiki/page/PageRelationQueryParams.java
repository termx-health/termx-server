package com.kodality.termx.wiki.page;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageRelationQueryParams extends QueryParams {
  private String type;
  private String target;
}
