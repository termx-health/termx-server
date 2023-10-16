package com.kodality.termx.terminology.terminology.mapset.statistics;

import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.mapset.association.MapSetAssociationRepository;
import com.kodality.termx.terminology.terminology.mapset.concept.MapSetConceptService;
import com.kodality.termx.terminology.terminology.mapset.version.MapSetVersionRepository;
import com.kodality.termx.terminology.terminology.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termx.ts.mapset.MapSetConceptQueryParams;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetResourceReference;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetVersionStatistics;
import com.kodality.termx.ts.mapset.MapSetVersionReference;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetStatisticsService {
  private final MapSetStatisticsRepository repository;
  private final MapSetVersionRepository mapSetVersionRepository;
  private final ConceptService codeSystemConceptService;
  private final ValueSetVersionConceptService valueSetConceptService;
  private final MapSetAssociationRepository mapSetAssociationRepository;
  private final MapSetConceptService mapSetConceptService;

  @Transactional
  public void calculate(String ms, String msv) {
    MapSetVersion version = getVersion(ms, msv);
    if (version == null) {
      return;
    }
    createStatisticsRecord(ms, version.getId(), calculate(version));
  }

  @Transactional
  public void createStatisticsRecord(String ms, Long msvId, MapSetVersionStatistics stat) {
    if (ms == null || msvId == null || stat == null) {
      return;
    }
    MapSetVersionStatistics statistics = load(ms, msvId);
    if (statistics == null) {
      statistics = new MapSetVersionStatistics().setMapSet(ms).setMapSetVersion(new MapSetVersionReference().setId(msvId));
    }
    statistics.setSourceConcepts(stat.getSourceConcepts());
    statistics.setEquivalent(stat.getEquivalent());
    statistics.setNoMap(stat.getNoMap());
    statistics.setNarrower(stat.getNarrower());
    statistics.setBroader(stat.getBroader());
    statistics.setUnmapped(stat.getUnmapped());
    statistics.setInactiveSources(stat.getInactiveSources());
    statistics.setInactiveTargets(stat.getInactiveTargets());
    statistics.setCreatedAt(OffsetDateTime.now());
    statistics.setCreatedBy(SessionStore.require().getUsername());
    repository.save(statistics);
  }

  public MapSetVersionStatistics load(String ms, Long msvId) {
    return repository.load(ms, msvId);
  }


  private MapSetVersionStatistics calculate(MapSetVersion version) {
    if (version == null || version.getId() == null || version.getScope() == null) {
      return null;
    }
    if (PublicationStatus.active.equals(version.getStatus()) && version.getStatistics() != null) {
      return version.getStatistics();
    }

    MapSetVersionStatistics statistics = new MapSetVersionStatistics();
    statistics.setEquivalent(getMappedConcepts(version.getId(), "equivalent,related-to"));
    statistics.setNoMap(getNoMapConcepts(version.getId()));
    statistics.setNarrower(getMappedConcepts(version.getId(), "source-is-narrower-than-target"));
    statistics.setBroader(getMappedConcepts(version.getId(), "source-is-broader-than-target"));
    statistics.setInactiveTargets(0); //TODO
    if ("code-system".equals(version.getScope().getSourceType()) && CollectionUtils.isNotEmpty(version.getScope().getSourceCodeSystems())) {
      List<MapSetResourceReference> sourceCodeSystems = version.getScope().getSourceCodeSystems();
      List<Concept> concepts = sourceCodeSystems.stream().flatMap(cs -> {
        ConceptQueryParams params = new ConceptQueryParams().setCodeSystem(cs.getId()).setCodeSystemVersion(cs.getVersion()).all();
        return codeSystemConceptService.query(params).getData().stream();
      }).toList();
      statistics.setInactiveSources(0); //TODO
      statistics.setSourceConcepts(concepts.size());
      statistics.setUnmapped(getUnmappedConcepts(version));
    }
    if ("value-set".equals(version.getScope().getSourceType()) && version.getScope().getSourceValueSet() != null) {
      MapSetResourceReference sourceValueSet = version.getScope().getSourceValueSet();
      List<ValueSetVersionConcept> concepts = valueSetConceptService.expand(sourceValueSet.getId(), sourceValueSet.getVersion());
      statistics.setInactiveSources(concepts.stream().filter(c -> !c.isActive()).toList().size());
      statistics.setSourceConcepts(concepts.size());
      statistics.setUnmapped(getUnmappedConcepts(version));
    }
    return statistics;
  }

  private Integer getMappedConcepts(Long mapSetVersionId, String relationship) {
    MapSetAssociationQueryParams params = new MapSetAssociationQueryParams().setMapSetVersionId(mapSetVersionId).setRelationships(relationship).limit(0);
    return mapSetAssociationRepository.query(params).getMeta().getTotal();
  }

  private Integer getNoMapConcepts(Long mapSetVersionId) {
    MapSetAssociationQueryParams params = new MapSetAssociationQueryParams().setMapSetVersionId(mapSetVersionId).setNoMap(true).limit(0);
    return mapSetAssociationRepository.query(params).getMeta().getTotal();
  }

  private Integer getUnmappedConcepts(MapSetVersion msv) {
    return mapSetConceptService.query(msv.getMapSet(), msv.getVersion(),
        new MapSetConceptQueryParams().setUnmapped(true).setType("source").limit(0)).getMeta().getTotal();
  }

  private MapSetVersion getVersion(String ms, String msv) {
    if (ms == null) {
      return null;
    }
    return Optional.ofNullable(msv).map(v -> mapSetVersionRepository.load(ms, v)).orElse(mapSetVersionRepository.loadLastVersion(ms));
  }
}
