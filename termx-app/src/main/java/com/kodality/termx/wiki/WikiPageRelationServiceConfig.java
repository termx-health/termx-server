package com.kodality.termx.wiki;

import com.kodality.termx.wiki.pagerelation.PageRelationServiceConfig;
import java.util.List;
import jakarta.inject.Singleton;

import static com.kodality.termx.wiki.page.PageRelationType.concept;
import static com.kodality.termx.wiki.page.PageRelationType.cs;
import static com.kodality.termx.wiki.page.PageRelationType.ms;
import static com.kodality.termx.wiki.page.PageRelationType.page;
import static com.kodality.termx.wiki.page.PageRelationType.vs;

@Singleton
public class WikiPageRelationServiceConfig implements PageRelationServiceConfig {
  @Override
  public List<String> getAllowedPageRelationTypes() {
    return List.of(cs, vs, ms, concept, page);
  }

  @Override
  public List<String> getAllowedPageRelationSystems() {
    return List.of("def", "csc", "vsc");
  }
}
