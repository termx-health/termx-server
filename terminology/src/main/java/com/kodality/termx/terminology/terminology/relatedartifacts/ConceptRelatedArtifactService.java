package com.kodality.termx.terminology.terminology.relatedartifacts;

import com.kodality.termx.core.sys.space.SpaceService;
import com.kodality.termx.core.wiki.PageProvider;
import com.kodality.termx.sys.space.Space;
import com.kodality.termx.sys.space.SpaceQueryParams;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.mapset.association.MapSetAssociationService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetService;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termx.ts.relatedartifact.RelatedArtifact;
import com.kodality.termx.ts.relatedartifact.RelatedArtifactType;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
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
public class ConceptRelatedArtifactService extends RelatedArtifactService {
  private final ConceptService conceptService;
  private final ValueSetService valueSetService;
  private final MapSetAssociationService mapSetAssociationService;
  private final Optional<PageProvider> pageProvider;
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
    return valueSets.stream().map(vs -> new RelatedArtifact().setId(vs.getId()).setType(RelatedArtifactType.vs)).collect(Collectors.toList());
  }

  private List<RelatedArtifact> findMapSets(String id) {
    Concept concept = conceptService.load(Long.valueOf(id)).orElseThrow();

    List<MapSetAssociation> associations = new ArrayList<>();
    associations.addAll(mapSetAssociationService.query(new MapSetAssociationQueryParams().setSourceCodeAndSystem(concept.getCode() + "|" + concept.getCodeSystem()).all()).getData());
    associations.addAll(mapSetAssociationService.query(new MapSetAssociationQueryParams().setTargetCodeAndSystem(concept.getCode() + "|" + concept.getCodeSystem()).all()).getData());
    return associations.stream().map(a -> new RelatedArtifact().setId(a.getMapSet()).setType(RelatedArtifactType.ms)).collect(Collectors.toList());
  }


  private List<RelatedArtifact> findPages(String id) {
    Concept concept = conceptService.load(Long.valueOf(id)).orElseThrow();

    List<PageContent> pages = pageProvider.map(p-> p.getRelatedPageContents(concept.getCodeSystem() + "|" + concept.getCode(), PageRelationType.concept)).orElse(List.of());

    String spaceIds = pages.stream().map(PageContent::getSpaceId).distinct().map(String::valueOf).collect(Collectors.joining(","));
    Map<Long, String> spaces = spaceService.query(new SpaceQueryParams().setIds(spaceIds).limit(spaceIds.split(",").length))
        .getData().stream().collect(Collectors.toMap(Space::getId, Space::getCode));

    return pages.stream().map(p -> new RelatedArtifact().setId(spaces.get(p.getSpaceId()) + "|" + p.getSlug()).setType(RelatedArtifactType.p)).collect(Collectors.toList());
  }
}
