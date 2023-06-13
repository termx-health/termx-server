package com.kodality.termserver.thesaurus.page;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageContent {
  private Long id;
  private Long pageId;
  private String name;
  private String slug;
  private String lang;
  private String content;
  private String contentType;

  // fixme: until full-blown authoring service appears
  private String modifiedBy;
  private LocalDateTime modifiedAt;
}
