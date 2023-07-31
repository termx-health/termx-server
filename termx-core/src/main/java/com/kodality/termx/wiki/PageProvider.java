package com.kodality.termx.wiki;

import com.kodality.termx.wiki.page.PageContent;
import java.util.ArrayList;
import java.util.List;

public abstract class PageProvider {

  public List<PageContent> getRelatedPageContents(String resourceId, String resourceType) {
    return new ArrayList<>();
  }
}
