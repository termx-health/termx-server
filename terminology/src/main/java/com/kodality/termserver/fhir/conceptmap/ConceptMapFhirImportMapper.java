package com.kodality.termserver.fhir.conceptmap;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.terminology.codesystem.CodeSystemService;
import com.kodality.termserver.terminology.valueset.ValueSetService;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.association.AssociationKind;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.ts.mapset.MapSet;
import com.kodality.termserver.ts.mapset.MapSetAssociation;
import com.kodality.termserver.ts.mapset.MapSetEntityVersion;
import com.kodality.termserver.ts.mapset.MapSetVersion;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetQueryParams;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroup;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ConceptMapFhirImportMapper {
  private final ValueSetService valueSetService;
  private final CodeSystemService codeSystemService;

  public MapSet mapMapSet(ConceptMap conceptMap) {
    MapSet ms = new MapSet();
    ms.setId(conceptMap.getId());
    ms.setUri(conceptMap.getUrl());
    ms.setNames(new LocalizedName(Map.of(Language.en, conceptMap.getName())));
    ms.setDescription(conceptMap.getDescription());
    ms.setVersions(List.of(mapVersion(conceptMap)));
    ms.setAssociations(mapAssociations(conceptMap));
    ms.setSourceValueSet(getValueSet(conceptMap.getSourceScopeUri() == null ? conceptMap.getSourceScopeCanonical() : conceptMap.getSourceScopeUri()));
    ms.setTargetValueSet(getValueSet(conceptMap.getTargetScopeUri() == null ? conceptMap.getTargetScopeCanonical() : conceptMap.getTargetScopeUri()));
    return ms;
  }

  private static MapSetVersion mapVersion(ConceptMap conceptMap) {
    MapSetVersion version = new MapSetVersion();
    version.setMapSet(conceptMap.getId());
    version.setVersion(conceptMap.getVersion());
    version.setSupportedLanguages(List.of(Language.en));
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(conceptMap.getDate() == null ? LocalDate.now() : LocalDate.from(conceptMap.getDate()));
    version.setSource(conceptMap.getPublisher());
    return version;
  }

  private List<MapSetAssociation> mapAssociations(ConceptMap conceptMap) {
    List<MapSetAssociation> associations = new ArrayList<>();
    if (CollectionUtils.isEmpty(conceptMap.getGroup())) {
      return associations;
    }

    List<String> codeSystemUris = new ArrayList<>();
    codeSystemUris.addAll(conceptMap.getGroup().stream().map(ConceptMapGroup::getSource).collect(Collectors.toSet()));
    codeSystemUris.addAll(conceptMap.getGroup().stream().map(ConceptMapGroup::getTarget).collect(Collectors.toSet()));
    CodeSystemQueryParams params = new CodeSystemQueryParams();
    params.setUri(String.join(",", codeSystemUris));
    params.setLimit(codeSystemUris.size());
    Map<String, CodeSystem> codeSystems = codeSystemService.query(params).getData().stream().collect(Collectors.toMap(CodeSystem::getUri, codeSystem -> codeSystem));

    conceptMap.getGroup().forEach(g -> g.getElement().forEach(element -> element.getTarget().forEach(target -> {
      MapSetAssociation association = new MapSetAssociation();
      association.setMapSet(conceptMap.getId());
      association.setStatus(PublicationStatus.active);
      association.setSource(new CodeSystemEntityVersion().setCodeSystem(Optional.ofNullable(codeSystems.get(g.getSource())).map(CodeSystem::getId).orElse(null))
          /*.setCodeSystemVersion(g.getSourceVersion())*/.setCode(element.getCode())); //FIXME
      association.setTarget(new CodeSystemEntityVersion().setCodeSystem(Optional.ofNullable(codeSystems.get(g.getTarget())).map(CodeSystem::getId).orElse(null))
          /*.setCodeSystemVersion(g.getTargetVersion())*/.setCode(target.getCode())); //FIXME
      association.setAssociationType(target.getRelationship());
      association.setVersions(List.of(new MapSetEntityVersion().setStatus(PublicationStatus.draft)));
      if (association.getSource() != null && association.getTarget() != null) {
        associations.add(association);
      }
    })));
    return associations;
  }

  public static List<AssociationType> mapAssociationTypes(ConceptMap conceptMap) {
    List<AssociationType> associationTypes = new ArrayList<>();
    if (CollectionUtils.isEmpty(conceptMap.getGroup())) {
      return associationTypes;
    }
    conceptMap.getGroup().forEach(g -> g.getElement().forEach(element -> element.getTarget().forEach(target -> {
      if (target.getCode() == null) {
        return;
      }
      associationTypes.add(new AssociationType(target.getRelationship(), AssociationKind.conceptMapEquivalence, true));
    })));
    return associationTypes;
  }

  private String getValueSet(String uri) {
    if (StringUtils.isEmpty(uri)) {
      return null;
    }
    ValueSetQueryParams params = new ValueSetQueryParams();
    params.setUri(uri);
    params.setLimit(1);
    return valueSetService.query(params).findFirst().map(ValueSet::getId).orElse(null);
  }
}
