package com.kodality.termserver.fhir.conceptmap;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetEntityVersion;
import com.kodality.termserver.mapset.MapSetVersion;
import com.kodality.termserver.ts.association.AssociationTypeService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.mapset.MapSetService;
import com.kodality.termserver.ts.mapset.MapSetVersionService;
import com.kodality.termserver.ts.mapset.association.MapSetAssociationService;
import com.kodality.termserver.ts.mapset.entity.MapSetEntityVersionService;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroup;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElement;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElementTarget;
import io.micronaut.core.util.CollectionUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptMapFhirImportService {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final AssociationTypeService associationTypeService;
  private final MapSetAssociationService mapSetAssociationService;
  private final MapSetEntityVersionService mapSetEntityVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  public void importMapSets(Parameters parameters, List<String> successes, List<String> warnings) {
    List<String> urls = CollectionUtils.isNotEmpty(parameters.getParameter()) ?
        parameters.getParameter().stream().filter(p -> "url".equals(p.getName())).map(Parameter::getValueString).toList() : Collections.emptyList();
    if (urls.isEmpty()) {
      throw ApiError.TE106.toApiException();
    }
    urls.forEach(url -> {
      try {
        importMapSet(url);
        successes.add(String.format("ConceptMap from resource %s imported", url));
      } catch (Exception e) {
        warnings.add(String.format("ConceptMap from resource %s was not imported due to error: %s", url, e.getMessage()));
      }
    });
  }

  @Transactional
  public void importMapSet(String url) {
    String resource = getResource(url);
    ConceptMap conceptMap = FhirMapper.fromJson(resource, ConceptMap.class);
    if (!ResourceType.conceptMap.equals(conceptMap.getResourceType())) {
      throw ApiError.TE107.toApiException();
    }
    MapSetVersion version = prepareMapSetAndVersion(ConceptMapFhirImportMapper.mapMapSet(conceptMap));
    List<MapSetAssociation> associations = findAssociations(conceptMap);
    importAssociations(associations, version);
  }

  private String getResource(String url) {
    log.info("Loading fhir map set from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.ISO_8859_1);
  }

  private MapSetVersion prepareMapSetAndVersion(MapSet mapSet) {
    log.info("Checking, the map set and version exists");
    Optional<MapSet> existingMapSet = mapSetService.load(mapSet.getId());
    if (existingMapSet.isEmpty()) {
      log.info("Map set {} does not exist, creating new", mapSet.getId());
      mapSetService.save(mapSet);
    }

    MapSetVersion version = mapSet.getVersions().get(0);
    Optional<MapSetVersion> existingVersion = mapSetVersionService.getVersion(mapSet.getId(), version.getVersion());
    if (existingVersion.isPresent() && existingVersion.get().getStatus().equals(PublicationStatus.active)) {
      throw ApiError.TE104.toApiException(Map.of("version", version.getVersion()));
    }
    log.info("Saving map set version {}", version.getVersion());
    mapSetVersionService.save(version);
    return version;
  }

  private List<MapSetAssociation> findAssociations(ConceptMap conceptMap) {
    List<MapSetAssociation> associations = new ArrayList<>();
    if (CollectionUtils.isEmpty(conceptMap.getGroup())) {
      return associations;
    }
    conceptMap.getGroup().forEach(g -> {
      g.getElement().forEach(element -> {
        element.getTarget().forEach(target -> {
          prepareAssociationType(target.getEquivalence());
          MapSetAssociation association = new MapSetAssociation();
          association.setMapSet(conceptMap.getId());
          association.setStatus(PublicationStatus.active);
          association.setSource(findSourceEntity(g, element));
          association.setTarget(findTargetEntity(g, target));
          association.setAssociationType(target.getEquivalence());
          association.setVersions(List.of(new MapSetEntityVersion().setStatus(PublicationStatus.draft)));
          if (association.getSource() != null && association.getTarget() != null) {
            associations.add(association);
          }
        });
      });
    });
    return associations;
  }

  private CodeSystemEntityVersion findSourceEntity(ConceptMapGroup g, ConceptMapGroupElement element) {
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setCodeSystemUri(g.getSource());
    params.setCodeSystemVersion(g.getSourceVersion());
    params.setCode(element.getCode());
    params.setLimit(1);
    return codeSystemEntityVersionService.query(params).findFirst().orElse(null);
  }

  private CodeSystemEntityVersion findTargetEntity(ConceptMapGroup g, ConceptMapGroupElementTarget element) {
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setCodeSystemUri(g.getTarget());
    params.setCodeSystemVersion(g.getTargetVersion());
    params.setCode(element.getCode());
    params.setLimit(1);
    return codeSystemEntityVersionService.query(params).findFirst().orElse(null);
  }

  public void prepareAssociationType(String code) {
    if (code == null) {
      return;
    }
    AssociationType associationType = new AssociationType();
    associationType.setCode(code);
    associationType.setDirected(true);
    associationType.setAssociationKind(AssociationKind.conceptMapEquivalence);
    associationTypeService.save(associationType);
  }

  @Transactional
  public void importAssociations(List<MapSetAssociation> associations, MapSetVersion version) {
    log.info("Creating '{}' associations", associations.size());
    associations.forEach(association -> {
      mapSetAssociationService.save(association, version.getMapSet());
      mapSetEntityVersionService.save(association.getVersions().get(0), association.getId());
      mapSetEntityVersionService.activate(association.getVersions().get(0).getId());
    });
    log.info("Associations created");

    log.info("Linking map set version and association versions");
    mapSetVersionService.saveEntityVersions(version.getId(), associations.stream().map(c -> c.getVersions().get(0)).collect(Collectors.toList()));

    log.info("Import finished.");
  }

}
