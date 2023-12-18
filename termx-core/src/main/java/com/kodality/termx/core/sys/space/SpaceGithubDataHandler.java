package com.kodality.termx.core.sys.space;

import com.kodality.termx.core.github.GithubService.GithubContent;
import com.kodality.termx.core.github.ResourceContentProvider.ResourceContent;
import com.kodality.termx.sys.space.Space;
import java.util.List;
import java.util.Map;

public interface SpaceGithubDataHandler {

  String getName();

  default String getDefaultDir() {
    return getName();
  }

  // contains absolute path
  default List<GithubContent> getCurrentContent(Space space) {
    String dir = space.getIntegration().getGithub().getDirs().get(getName());
    return getContent(space.getId()).stream().map(e -> {
      return new GithubContent()
          .setPath(dir + "/" + e.getName())
          .setContent(e.getContent())
          .setEncoding(e.getEncoding());
    }).toList();
  }

  List<ResourceContent> getContent(Long spaceId);

  // file name (relative path) -> content. null content = should delete file
  void saveContent(Long spaceId, Map<String, String> content);
}
