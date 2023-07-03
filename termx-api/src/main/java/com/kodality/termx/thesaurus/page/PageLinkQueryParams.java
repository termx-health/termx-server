package com.kodality.termx.thesaurus.page;

import com.kodality.commons.model.QueryParams;
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

  public interface Ordering {
    String orderNumber = "orderNumber";
  }
}
