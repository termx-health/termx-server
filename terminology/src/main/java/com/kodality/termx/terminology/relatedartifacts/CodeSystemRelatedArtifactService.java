package com.kodality.termx.terminology.relatedartifacts;

import com.kodality.termx.sys.space.Space;
import com.kodality.termx.sys.space.SpaceQueryParams;
import com.kodality.termx.sys.space.SpaceService;
import com.kodality.termx.terminology.mapset.version.MapSetVersionService;
import com.kodality.termx.terminology.valueset.ValueSetService;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.termx.ts.mapset.MapSetVersionQueryParams;
import com.kodality.termx.ts.relatedartifact.RelatedArtifact;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.wiki.PageProvider;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageRelationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemRelatedArtifactService extends RelatedArtifactService {
  private final ValueSetService valueSetService;
  private final MapSetVersionService mapSetVersionService;
  private final PageProvider pageProvider;
  private final SpaceService spaceService;

  @Override
  public String getResourceType() {
    return "CodeSystem";
  }

  @Override
  public List<RelatedArtifact> findRelatedArtifacts(String id) {
    List<RelatedArtifact> artifacts = new ArrayList<>();
    artifacts.addAll(findValueSets(id));
    artifacts.addAll(findMapSets(id));
    artifacts.addAll(findPages(id));
    artifacts.addAll(findSpaces(id));
    return artifacts;
  }

  private List<RelatedArtifact> findValueSets(String id) {
    List<ValueSet> valueSets = valueSetService.query(new ValueSetQueryParams().setCodeSystem(id).all()).getData();
    return valueSets.stream().map(vs -> new RelatedArtifact().setId(vs.getId()).setType("ValueSet")).collect(Collectors.toList());
  }

  private List<RelatedArtifact> findMapSets(String id) {
    List<MapSetVersion> versions = new ArrayList<>();
    versions.addAll(mapSetVersionService.query(new MapSetVersionQueryParams().setScopeSourceCodeSystem(id).all()).getData());
    versions.addAll(mapSetVersionService.query(new MapSetVersionQueryParams().setScopeTargetCodeSystem(id).all()).getData());
    return versions.stream().map(v -> new RelatedArtifact().setId(v.getMapSet()).setType("MapSet")).collect(Collectors.toList());
  }

  private List<RelatedArtifact> findPages(String id) {
    List<PageContent> pages = pageProvider.getRelatedPageContents(id, PageRelationType.cs);

    String spaceIds = pages.stream().map(PageContent::getSpaceId).distinct().map(String::valueOf).collect(Collectors.joining(","));
    Map<Long, String> spaces = spaceService.query(new SpaceQueryParams().setIds(spaceIds).limit(spaceIds.split(",").length))
        .getData().stream().collect(Collectors.toMap(Space::getId, Space::getCode));

    return pages.stream().map(p -> new RelatedArtifact().setId(spaces.get(p.getSpaceId()) + "|" + p.getSlug()).setType("Page")).collect(Collectors.toList());
  }

  private List<RelatedArtifact> findSpaces(String id) {
    return spaceService.query(new SpaceQueryParams().setResource("code-system|" + id).all()).getData().stream().map(s ->
        new RelatedArtifact().setId(s.getCode() + "|" + s.getId()).setType("Space")).toList();
  }
}
