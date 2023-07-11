package com.kodality.termx.thesaurus.page;

import com.kodality.commons.model.CodeName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageRelation {
  private Long id;
  private Long pageId;
  private Long spaceId;
  private CodeName content;

  private String type;
  private String target;
}
