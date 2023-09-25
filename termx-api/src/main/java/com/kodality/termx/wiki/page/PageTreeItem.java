package com.kodality.termx.wiki.page;


import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageTreeItem {
  private Long pageId;
  private Long parentPageId;
  private Map<String, PageTreeItemContent> contents;
  private List<PageTreeItem> children;


  @Getter
  @Setter
  public static class PageTreeItemContent {
    private Long id;
    private String name;
    private String slug;
  }
}
