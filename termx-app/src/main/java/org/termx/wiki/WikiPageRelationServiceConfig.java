package org.termx.wiki;

import org.termx.wiki.pagerelation.PageRelationServiceConfig;
import java.util.List;
import jakarta.inject.Singleton;

import static org.termx.wiki.page.PageRelationType.concept;
import static org.termx.wiki.page.PageRelationType.cs;
import static org.termx.wiki.page.PageRelationType.ms;
import static org.termx.wiki.page.PageRelationType.page;
import static org.termx.wiki.page.PageRelationType.sd;
import static org.termx.wiki.page.PageRelationType.vs;

@Singleton
public class WikiPageRelationServiceConfig implements PageRelationServiceConfig {
  @Override
  public List<String> getAllowedPageRelationTypes() {
    return List.of(cs, vs, ms, sd, concept, page);
  }

  @Override
  public List<String> getAllowedPageRelationSystems() {
    return List.of("def", "csc", "vsc");
  }
}
