package com.kodality.termserver.thesaurus.page;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageLink {
  private Long id;
  private Long sourceId;
  private Long targetId;
  private Integer orderNumber;
}
