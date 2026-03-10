package org.termx.modeler.structuredefinition.relatedartifacts;

import org.termx.core.sys.space.SpaceService;
import org.termx.core.wiki.PageProvider;
import org.termx.sys.space.Space;
import org.termx.sys.space.SpaceQueryParams;
import org.termx.terminology.terminology.relatedartifacts.RelatedArtifactService;
import org.termx.ts.relatedartifact.RelatedArtifact;
import org.termx.ts.relatedartifact.RelatedArtifactType;
import org.termx.wiki.page.PageContent;
import org.termx.wiki.page.PageRelationType;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@RequiredArgsConstructor
public class StructureDefinitionRelatedArtifactService extends RelatedArtifactService {
  private final Optional<PageProvider> pageProvider;
  private final SpaceService spaceService;

  @Override
  public String getResourceType() {
    return "StructureDefinition";
  }

  @Override
  public List<RelatedArtifact> findRelatedArtifacts(String id) {
    List<RelatedArtifact> artifacts = new ArrayList<>();
    artifacts.addAll(findPages(id));
    artifacts.addAll(findSpaces(id));
    return artifacts;
  }

  private List<RelatedArtifact> findPages(String id) {
    List<PageContent> pages = pageProvider.map(p -> p.getRelatedPageContents(id, PageRelationType.sd)).orElse(List.of());
    if (pages.isEmpty()) return List.of();
    String spaceIds = pages.stream().map(PageContent::getSpaceId).distinct().map(String::valueOf).collect(Collectors.joining(","));
    Map<Long, String> spaces = spaceService.query(new SpaceQueryParams().setIds(spaceIds).limit(spaceIds.split(",").length))
        .getData().stream().collect(Collectors.toMap(Space::getId, Space::getCode));
    return pages.stream().map(p -> new RelatedArtifact().setId(spaces.get(p.getSpaceId()) + "|" + p.getSlug()).setType(RelatedArtifactType.p)).toList();
  }

  private List<RelatedArtifact> findSpaces(String id) {
    return spaceService.query(new SpaceQueryParams().setResource("structure-definition|" + id).all()).getData().stream()
        .map(s -> new RelatedArtifact().setId(s.getCode() + "|" + s.getId()).setType(RelatedArtifactType.s)).toList();
  }
}
