package com.kodality.termserver.thesaurus;


import com.kodality.termserver.thesaurus.pagecontent.PageContent;
import com.kodality.termserver.thesaurus.pagerelation.PageRelation;
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
  private boolean leaf;
  private List<PageContent> contents;
  private List<PageRelation> relations;
}
