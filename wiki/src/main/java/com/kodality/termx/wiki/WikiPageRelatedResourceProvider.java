package com.kodality.termx.wiki;


import java.util.Optional;

public interface WikiPageRelatedResourceProvider {
  String getRelationType();
  String gerResourceName();

  Optional<String> getContent(String target);
}
