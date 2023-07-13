package com.kodality.termx.wiki.page;


import java.time.OffsetDateTime;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Page {
  private Long id;
  @NotNull
  private Long spaceId;
  private String status;
  private PageSettings settings;
  private boolean leaf;

  private List<PageContent> contents;
  private List<PageLink> links;
  private List<PageTag> tags;
  private List<PageRelation> relations;

  private OffsetDateTime createdAt;
  private String createdBy;
  private OffsetDateTime modifiedAt;
  private String modifiedBy;


  @Getter
  @Setter
  public static class PageSettings {
    private long templateId;
  }
}
