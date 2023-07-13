package com.kodality.termx.wiki.page;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageLinkMoveRequest {
  private Long parentLinkId;
  private Long siblingLinkId;
  private String action; // before, after
}
