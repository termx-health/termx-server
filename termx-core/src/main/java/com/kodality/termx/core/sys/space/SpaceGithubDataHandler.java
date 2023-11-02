package com.kodality.termx.core.sys.space;

import com.kodality.termx.core.github.GithubService.GithubContent;
import com.kodality.termx.core.github.GithubService.GithubContent.GithubContentEncoding;
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
    return getContent(space.getId()).entrySet().stream().map(e -> {
      return new GithubContent()
          .setPath(dir + "/" + e.getKey())
          .setContent(e.getValue().content)
          .setEncoding(e.getValue().encoding);
    }).toList();
  }

  // file name (relative path) -> content
  Map<String, SpaceGithubData> getContent(Long spaceId);

  // file name (relative path) -> content. null content = should delete file
  void saveContent(Long spaceId, Map<String, String> content);


  record SpaceGithubData(String content, String encoding) {
    public SpaceGithubData(String content) {
      this(content, GithubContentEncoding.utf8);
    }
  }
}
