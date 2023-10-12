package com.kodality.termx.wiki.pagerelation;

import java.util.List;

public interface PageRelationServiceConfig {
  default List<String> getAllowedPageRelationTypes() {
    return List.of();
  }

  default List<String> getAllowedPageRelationSystems() {
    return List.of();
  }
}
