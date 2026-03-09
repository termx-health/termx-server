package org.termx.terminology.terminology.relatedartifacts;

import org.termx.core.sys.space.SpaceService;
import org.termx.core.wiki.PageProvider;
import org.termx.sys.space.Space;
import org.termx.sys.space.SpaceQueryParams;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.terminology.terminology.mapset.association.MapSetAssociationService;
import org.termx.terminology.terminology.valueset.ValueSetService;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.mapset.MapSetAssociation;
import org.termx.ts.mapset.MapSetAssociationQueryParams;
import org.termx.ts.relatedartifact.RelatedArtifact;
import org.termx.ts.relatedartifact.RelatedArtifactType;
import org.termx.ts.valueset.ValueSet;
import org.termx.ts.valueset.ValueSetQueryParams;
import org.termx.wiki.page.PageContent;
import org.termx.wiki.page.PageRelationType;
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
    return valueSets.stream().map(vs -> new RelatedArtifact().setId(vs.getId()).setType(RelatedArtifactType.vs)).toList();
  }

  private List<RelatedArtifact> findMapSets(String id) {
    Concept concept = conceptService.load(Long.valueOf(id)).orElseThrow();

    List<MapSetAssociation> associations = new ArrayList<>();
    associations.addAll(mapSetAssociationService.query(new MapSetAssociationQueryParams().setSourceCodeAndSystem(concept.getCode() + "|" + concept.getCodeSystem()).all()).getData());
    associations.addAll(mapSetAssociationService.query(new MapSetAssociationQueryParams().setTargetCodeAndSystem(concept.getCode() + "|" + concept.getCodeSystem()).all()).getData());
    return associations.stream().map(a -> new RelatedArtifact().setId(a.getMapSet()).setType(RelatedArtifactType.ms)).toList();
  }


  private List<RelatedArtifact> findPages(String id) {
    Concept concept = conceptService.load(Long.valueOf(id)).orElseThrow();

    List<PageContent> pages = pageProvider.map(p-> p.getRelatedPageContents(concept.getCodeSystem() + "|" + concept.getCode(), PageRelationType.concept)).orElse(List.of());

    String spaceIds = pages.stream().map(PageContent::getSpaceId).distinct().map(String::valueOf).collect(Collectors.joining(","));
    Map<Long, String> spaces = spaceService.query(new SpaceQueryParams().setIds(spaceIds).limit(spaceIds.split(",").length))
        .getData().stream().collect(Collectors.toMap(Space::getId, Space::getCode));

    return pages.stream().map(p -> new RelatedArtifact().setId(spaces.get(p.getSpaceId()) + "|" + p.getSlug()).setType(RelatedArtifactType.p)).toList();
  }
}
