package com.kodality.termx.sys.space;


import com.kodality.termx.github.GithubService.GithubContent;
import com.kodality.termx.sys.space.Space.SpaceIntegrationImplementationGuide;
import com.kodality.termx.wiki.PageProvider;
import com.kodality.termx.wiki.page.PageContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceGithubDataImplementationGuideHandler {
  private final PageProvider pageProvider;

  public List<GithubContent> getCurrentContent(Space space) {
    return getContent(space).entrySet().stream().map(e -> new GithubContent().setPath(e.getKey()).setContent(e.getValue())).toList();
  }

  public Map<String, String> getContent(Space space) {
    if (space.getIntegration() == null || space.getIntegration().getGithub() == null || space.getIntegration().getGithub().getIg() == null) {
      return Map.of();
    }
    Map<String, String> result = new HashMap<>();
    result.put("sushi-config.yaml", generateSushiConfig(space.getIntegration().getGithub().getIg()));
    return result;
  }

  private String generateSushiConfig(SpaceIntegrationImplementationGuide ig) {
    Map<Long, PageContent> pages = ig.getPageContents().stream().collect(Collectors.toMap(i -> i, pageProvider::load));
    List<String> parts = new ArrayList<>();
    parts.add(ig.getHeader());

    if (CollectionUtils.isNotEmpty(ig.getPageContents())) {
      parts.add("");
      parts.add("pages:");
      ig.getPageContents().stream().map(pages::get).forEach(pc -> {
        parts.add("  " + pc.getSlug() + ".md:");
        parts.add("    title: " + pc.getName());
      });
    }
    if (CollectionUtils.isNotEmpty(ig.getMenu())) {
      parts.add("");
      parts.add("menu:");
      ig.getMenu().forEach(m -> {
        parts.add("  " + m.getName() + ": " + (m.getPage() != null ? pages.get(m.getPage()).getSlug() + ".html" : ""));
        if (m.getChildren() != null) {
          m.getChildren().forEach(c -> {
            parts.add("    " + c.getName() + ": " + pages.get(c.getPage()).getSlug() + ".html");
          });
        }
      });
    }

    return String.join("\n", parts);
  }

}
