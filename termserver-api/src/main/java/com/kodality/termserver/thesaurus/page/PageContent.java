package com.kodality.termserver.thesaurus.page;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageContent {
  private Long id;
  @NotNull
  private Long pageId;
  @JsonIgnore
  private Long spaceId;
  private String name;
  private String slug;
  private String lang;
  private String content;
  private String contentType;
}
