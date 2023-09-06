package com.kodality.termx.wiki.page;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageCommentQueryParams extends QueryParams {
  private String ids;
  private String parentIds;
  private String pageContentIds;
  private String statuses;
  private String statusesNe;
  private String contentContains;
  private Boolean replies;
}
