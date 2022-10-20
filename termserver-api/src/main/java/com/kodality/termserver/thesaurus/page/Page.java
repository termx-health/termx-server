package com.kodality.termserver.thesaurus.page;


import java.util.List;
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
  private boolean leaf;
  private List<PageContent> contents;
  private List<PageLink> links;
  private List<PageTag> tags;
  private List<PageRelation> relations;
}
