package com.kodality.termserver.thesaurus.page;


import java.time.LocalDateTime;
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
  private String status;
  private Long templateId;
  @NotNull
  private Long spaceId;
  private boolean leaf;
  private List<PageContent> contents;
  private List<PageLink> links;
  private List<PageTag> tags;
  private List<PageRelation> relations;

  private LocalDateTime createdAt;
  private String createdBy;
  private LocalDateTime modifiedAt;
  private String modifiedBy;
}
