package com.kodality.termx.wiki.page;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageContentQueryParams extends QueryParams {
  private String ids;
  private String slugs;
  private String textContains;
  private String spaceIds;
  private String relations; //targetType|targetId1,targetType|targetId2

  private List<Long> permittedSpaceIds;
}
