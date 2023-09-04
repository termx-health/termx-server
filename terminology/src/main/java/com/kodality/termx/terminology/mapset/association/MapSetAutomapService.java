package com.kodality.termx.terminology.mapset.association;


import com.kodality.termx.terminology.mapset.concept.MapSetConceptService;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociation.MapSetAssociationEntity;
import com.kodality.termx.ts.mapset.MapSetAutomapRequest;
import com.kodality.termx.ts.mapset.MapSetConcept;
import com.kodality.termx.ts.mapset.MapSetConceptQueryParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetAutomapService {
  private final MapSetConceptService mapSetConceptService;
  private final MapSetAssociationService mapSetAssociationService;

  @Transactional
  public void automap(String ms, String msv, MapSetAutomapRequest request) {
    List<MapSetConcept> sourceConcepts = mapSetConceptService.query(ms, msv, new MapSetConceptQueryParams().setType("source").all()).getData();
    List<MapSetConcept> targetConcepts = mapSetConceptService.query(ms, msv, new MapSetConceptQueryParams().setType("target").all()).getData();

    List<MapSetAssociation> associationsMappedByCode = request.isMapByCode() ? mapByCode(sourceConcepts, targetConcepts) : new ArrayList<>();
    List<MapSetAssociation> associationsMappedByDesignation = request.isMapByDesignation() ? mapByDesignation(sourceConcepts, targetConcepts, request) : new ArrayList<>();

    if (request.isMapByCode() && !request.isMapByDesignation()) {
      mapSetAssociationService.batchUpsert(associationsMappedByCode, ms, msv);
    }

    if (!request.isMapByCode() && request.isMapByDesignation()) {
      mapSetAssociationService.batchUpsert(associationsMappedByDesignation, ms, msv);
    }

    if (request.isMapByCode() && request.isMapByDesignation()) {
      mapSetAssociationService.batchUpsert(getCommon(associationsMappedByCode, associationsMappedByDesignation), ms, msv);
    }
  }

  private List<MapSetAssociation> mapByCode(List<MapSetConcept> sourceConcepts, List<MapSetConcept> targetConcepts) {
    Map<String, List<MapSetConcept>> sources = sourceConcepts.stream().collect(Collectors.groupingBy(c -> c.getCode().toLowerCase()));
    Map<String, List<MapSetConcept>> targets = targetConcepts.stream().collect(Collectors.groupingBy(c -> c.getCode().toLowerCase()));
    return automap(sources, targets);
  }


  private List<MapSetAssociation> mapByDesignation(List<MapSetConcept> sourceConcepts, List<MapSetConcept> targetConcepts, MapSetAutomapRequest request) {
    Map<String, List<MapSetConcept>> sources = groupByDesignations(sourceConcepts, request);
    Map<String, List<MapSetConcept>> targets = groupByDesignations(targetConcepts, request);
    return automap(sources, targets);
  }

  private Map<String, List<MapSetConcept>> groupByDesignations(List<MapSetConcept> concepts, MapSetAutomapRequest request) {
    return concepts.stream().flatMap(s -> Optional.ofNullable(s.getDesignations()).orElse(List.of()).stream()
            .filter(d -> {
              boolean prop = request.getSourceProperty() == null || d.getDesignationType().equals(request.getSourceProperty());
              boolean lang = request.getSourceLanguage() == null || d.getLanguage().equals(request.getSourceLanguage());
              return prop && lang;
            }).map(d -> Pair.of(d.getLanguage() + d.getName().toLowerCase(), s)))
        .collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));
  }

  private List<MapSetAssociation> automap(Map<String, List<MapSetConcept>> sources, Map<String, List<MapSetConcept>> targets) {
    return sources.keySet().stream().filter(targets::containsKey).flatMap(k -> sources.get(k).stream().flatMap(s -> targets.get(k).stream()
        .filter(t -> associationNotExist(s, t))
        .map(t -> toAssociation(s, t)))
    ).toList();
  }

  private MapSetAssociation toAssociation(MapSetConcept s, MapSetConcept t) {
    MapSetAssociation association = new MapSetAssociation();
    association.setRelationship("equivalent");
    association.setSource(new MapSetAssociationEntity().setCode(s.getCode()).setCodeSystem(s.getCodeSystem())
        .setDisplay(s.getDisplay() == null ? null : s.getDisplay().getName()));
    association.setTarget(new MapSetAssociationEntity().setCode(t.getCode()).setCodeSystem(t.getCodeSystem())
        .setDisplay(t.getDisplay() == null ? null : t.getDisplay().getName()));
    return association;
  }

  private boolean associationNotExist(MapSetConcept s, MapSetConcept t) {
    return s.getAssociations() == null || s.getAssociations().stream()
        .noneMatch(a -> a.getTarget() != null && a.getTarget().getCode() != null && t.getCode().equals(a.getTarget().getCode().toLowerCase()));
  }

  private List<MapSetAssociation> getCommon(List<MapSetAssociation> mappedByCode, List<MapSetAssociation> mappedByDesignation) {
    return mappedByCode.stream().filter(mbc -> mappedByDesignation.stream().anyMatch(mbd ->
            mbc.getSource().getCode().equals(mbd.getSource().getCode()) &&
                mbc.getTarget().getCode().equals(mbd.getTarget().getCode())))
        .toList();
  }

}
