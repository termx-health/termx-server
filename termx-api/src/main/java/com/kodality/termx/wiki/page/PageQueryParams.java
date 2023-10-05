package com.kodality.termx.wiki.page;

import com.kodality.commons.model.QueryParams;
import java.util.List;
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
  private String codes;
  private String spaceIds;
  private Long rootId;
  private String textContains;
  private String slugs;

  private List<Long> permittedSpaceIds;

  public interface Ordering {
    String modified = "modified";
  }
}
