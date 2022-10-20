package com.kodality.termserver.thesaurus.page;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageRelation {
  private Long id;
  private String target;
  private String type;
}
