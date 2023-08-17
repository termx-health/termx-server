package com.kodality.termx.sys.space;

import java.util.Map;

public interface SpaceGithubDataHandler {

  String getName();

  //file name -> content
  Map<String, String> getContent(Long spaceId);

  //file name -> content. null content = should delete file
  void saveContent(Long spaceId, Map<String, String> content);
}
