package com.kodality.termx.wiki.page;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageLinkQueryParams extends QueryParams {
  private Boolean root;
  private String sourceIds;
  private String targetIds;
  private String spaceIds;

  private List<Long> permittedSpaceIds;

  public interface Ordering {
    String orderNumber = "orderNumber";
  }
}
