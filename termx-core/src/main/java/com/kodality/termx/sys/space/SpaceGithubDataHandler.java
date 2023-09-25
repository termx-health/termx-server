package com.kodality.termx.sys.space;

import com.kodality.termx.github.GithubService.GithubContent;
import java.util.List;
import java.util.Map;

public interface SpaceGithubDataHandler {

  String getName();

  default String getDefaultDir() {
    return getName();
  }

  default List<GithubContent> getCurrentContent(Space space) {
    String dir = space.getIntegration().getGithub().getDirs().get(getName());
    return getContent(space.getId()).entrySet().stream().map(e -> new GithubContent().setPath(dir + "/" + e.getKey()).setContent(e.getValue())).toList();
  }

  //file name -> content
  Map<String, String> getContent(Long spaceId);

  //file name -> content. null content = should delete file
  void saveContent(Long spaceId, Map<String, String> content);
}
