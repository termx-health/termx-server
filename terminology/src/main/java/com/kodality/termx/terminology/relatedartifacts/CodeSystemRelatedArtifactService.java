package com.kodality.termx.terminology.relatedartifacts;

import com.kodality.termx.terminology.mapset.MapSetService;
import com.kodality.termx.terminology.valueset.ValueSetService;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import com.kodality.termx.ts.relatedartifact.RelatedArtifact;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemRelatedArtifactService extends RelatedArtifactService {
  private final ValueSetService valueSetService;
  private final MapSetService mapSetService;

  @Override
  public String getResourceType() {
    return "CodeSystem";
  }

  @Override
  public List<RelatedArtifact> findRelatedArtifacts(String id) {
    List<RelatedArtifact> artifacts = new ArrayList<>();
    artifacts.addAll(findValueSets(id));
    artifacts.addAll(findMapSets(id));
    return artifacts;
  }

  private List<RelatedArtifact> findValueSets(String id) {
    List<ValueSet> valueSets = valueSetService.query(new ValueSetQueryParams().setCodeSystem(id).all()).getData();
    return valueSets.stream().map(vs -> new RelatedArtifact().setId(vs.getId()).setType("ValueSet")).collect(Collectors.toList());
  }

  private List<RelatedArtifact> findMapSets(String id) {
    List<MapSet> mapSets = new ArrayList<>();
    mapSets.addAll(mapSetService.query(new MapSetQueryParams().setAssociationSourceSystem(id).all()).getData());
    mapSets.addAll(mapSetService.query(new MapSetQueryParams().setAssociationTargetSystem(id).all()).getData());
    return mapSets.stream().map(ms -> new RelatedArtifact().setId(ms.getId()).setType("MapSet")).collect(Collectors.toList());
  }

}
