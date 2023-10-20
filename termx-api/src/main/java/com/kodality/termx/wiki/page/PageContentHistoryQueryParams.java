package com.kodality.termx.wiki.page;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageContentHistoryQueryParams extends QueryParams {
  private String ids;
  private boolean summary;

  private List<Long> permittedPageIds;
  private List<Long> permittedPageContentIds;

  public interface Ordering {
    String modified = "modified";
  }
}
