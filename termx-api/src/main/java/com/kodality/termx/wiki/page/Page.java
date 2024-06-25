package com.kodality.termx.wiki.page;


import java.util.List;
import jakarta.validation.constraints.NotNull;
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
  private String code;
  private String status;
  private PageSettings settings;
  private boolean leaf;

  private List<PageContent> contents;
  private List<PageLink> links;
  private List<PageTag> tags;
  private List<PageRelation> relations;


  @Getter
  @Setter
  public static class PageSettings {
    private Long templateId;
  }

  @Getter
  @Setter
  public static class PageAttachment {
    private String fileId;
    private String fileName;
    private String contentType;
  }
}
