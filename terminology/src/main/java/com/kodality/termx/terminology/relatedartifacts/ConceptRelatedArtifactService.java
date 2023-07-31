package com.kodality.termx.terminology.relatedartifacts;

import com.kodality.termx.sys.space.Space;
import com.kodality.termx.sys.space.SpaceQueryParams;
import com.kodality.termx.sys.space.SpaceService;
import com.kodality.termx.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.mapset.MapSetService;
import com.kodality.termx.terminology.valueset.ValueSetService;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
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
public class ConceptRelatedArtifactService extends RelatedArtifactService {
  private final ConceptService conceptService;
  private final ValueSetService valueSetService;
  private final MapSetService mapSetService;
  private final PageProvider pageProvider;
  private final SpaceService spaceService;

  @Override
  public String getResourceType() {
    return "Concept";
  }

  @Override
  public List<RelatedArtifact> findRelatedArtifacts(String id) {
    List<RelatedArtifact> artifacts = new ArrayList<>();
    artifacts.addAll(findValueSets(id));
    artifacts.addAll(findMapSets(id));
    artifacts.addAll(findPages(id));
    return artifacts;
  }

  private List<RelatedArtifact> findValueSets(String id) {
    List<ValueSet> valueSets = valueSetService.query(new ValueSetQueryParams().setConceptId(Long.valueOf(id)).all()).getData();
    return valueSets.stream().map(vs -> new RelatedArtifact().setId(vs.getId()).setType("ValueSet")).collect(Collectors.toList());
  }

  private List<RelatedArtifact> findMapSets(String id) {
    List<MapSet> mapSets = mapSetService.query(new MapSetQueryParams().setAssociationSourceId(Long.valueOf(id)).all()).getData();
    mapSets.addAll(mapSetService.query(new MapSetQueryParams().setAssociationTargetId(Long.valueOf(id)).all()).getData());
    return mapSets.stream().map(ms -> new RelatedArtifact().setId(ms.getId()).setType("MapSet")).collect(Collectors.toList());
  }


  private List<RelatedArtifact> findPages(String id) {
    Concept concept = conceptService.load(Long.valueOf(id)).orElseThrow();

    List<PageContent> pages = pageProvider.getRelatedPageContents(concept.getCodeSystem() + "|" + concept.getCode(), PageRelationType.concept);

    String spaceIds = pages.stream().map(PageContent::getSpaceId).distinct().map(String::valueOf).collect(Collectors.joining(","));
    Map<Long, String> spaces = spaceService.query(new SpaceQueryParams().setIds(spaceIds).limit(spaceIds.split(",").length))
        .getData().stream().collect(Collectors.toMap(Space::getId, Space::getCode));

    return pages.stream().map(p -> new RelatedArtifact().setId(spaces.get(p.getSpaceId()) + "|" + p.getSlug()).setType("Page")).collect(Collectors.toList());
  }
}
