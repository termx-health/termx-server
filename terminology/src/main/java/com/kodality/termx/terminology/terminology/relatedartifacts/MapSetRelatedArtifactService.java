package com.kodality.termx.terminology.terminology.relatedartifacts;

import com.kodality.termx.core.sys.space.SpaceService;
import com.kodality.termx.core.wiki.PageProvider;
import com.kodality.termx.sys.space.Space;
import com.kodality.termx.sys.space.SpaceQueryParams;
import com.kodality.termx.ts.relatedartifact.RelatedArtifact;
import com.kodality.termx.ts.relatedartifact.RelatedArtifactType;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageRelationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class MapSetRelatedArtifactService extends RelatedArtifactService {
  private final Optional<PageProvider> pageProvider;
  private final SpaceService spaceService;

  @Override
  public String getResourceType() {
    return "MapSet";
  }

  @Override
  public List<RelatedArtifact> findRelatedArtifacts(String id) {
    List<RelatedArtifact> artifacts = new ArrayList<>();
    artifacts.addAll(findPages(id));
    artifacts.addAll(findSpaces(id));
    return artifacts;
  }

  private List<RelatedArtifact> findPages(String id) {
    List<PageContent> pages = pageProvider.map(p-> p.getRelatedPageContents(id, PageRelationType.ms)).orElse(List.of());

    String spaceIds = pages.stream().map(PageContent::getSpaceId).distinct().map(String::valueOf).collect(Collectors.joining(","));
    Map<Long, String> spaces = spaceService.query(new SpaceQueryParams().setIds(spaceIds).limit(spaceIds.split(",").length))
        .getData().stream().collect(Collectors.toMap(Space::getId, Space::getCode));

    return pages.stream().map(p -> new RelatedArtifact().setId(spaces.get(p.getSpaceId()) + "|" + p.getSlug()).setType(RelatedArtifactType.p)).collect(Collectors.toList());
  }

  private List<RelatedArtifact> findSpaces(String id) {
    return spaceService.query(new SpaceQueryParams().setResource("map-set|" + id).all()).getData().stream().map(s ->
        new RelatedArtifact().setId(s.getCode() + "|" + s.getId()).setType(RelatedArtifactType.s)).toList();
  }
}
