package com.kodality.termx.wiki.page;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageContent {
  private Long id;
  private Long pageId;
  private Long spaceId;
  private String name;
  private String slug;
  private String lang;
  private String content;
  private String contentType;

  private OffsetDateTime createdAt;
  private String createdBy;
  private OffsetDateTime modifiedAt;
  private String modifiedBy;
}
