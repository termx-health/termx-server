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
  private String ids;
  private String idsNe;
  private String spaceIds;
  private Long rootId;
  private String textContains;
  private String slugs;

  public interface Ordering {
    String modified = "modified";
  }
}
