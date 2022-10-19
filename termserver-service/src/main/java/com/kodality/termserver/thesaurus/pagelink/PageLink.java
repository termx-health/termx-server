package com.kodality.termserver.thesaurus.pagelink;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageLink {
  private Long id;
  private Long targetId;
  private Long sourceId;
  private Integer orderNumber;
}
