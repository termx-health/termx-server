package com.kodality.termx.fhir.conceptmap;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.fhir.BaseFhirMapper;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.terminology.valueset.ValueSetService;
import com.kodality.termx.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.ts.Copyright;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationKind;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociation.MapSetAssociationEntity;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetResourceReference;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetVersionScope;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroup;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElement;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElementTarget;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

@Context
public class ConceptMapFhirMapper extends BaseFhirMapper {
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;

  private static Optional<String> termxWebUrl;

  public ConceptMapFhirMapper(CodeSystemService codeSystemService, CodeSystemVersionService codeSystemVersionService,
                              ValueSetService valueSetService, ValueSetVersionService valueSetVersionService,
                              @Value("${termx.web-url}") Optional<String> termxWebUrl) {
    this.codeSystemService = codeSystemService;
    this.codeSystemVersionService = codeSystemVersionService;
    this.valueSetService = valueSetService;
    this.valueSetVersionService = valueSetVersionService;
    ConceptMapFhirMapper.termxWebUrl = termxWebUrl;
  }

  // -------------- TO FHIR --------------

  public static String toFhirId(MapSet mapSet, MapSetVersion version) {
    return mapSet.getId() + "@" + version.getVersion();
  }

  public String toFhirJson(MapSet ms, MapSetVersion msv, List<Provenance> provenances) {
    return addTranslationExtensions(FhirMapper.toJson(toFhir(ms, msv, provenances)), ms, msv);
  }

  public com.kodality.zmei.fhir.resource.terminology.ConceptMap toFhir(MapSet mapSet, MapSetVersion version, List<Provenance> provenances) {
    com.kodality.zmei.fhir.resource.terminology.ConceptMap fhirConceptMap = new com.kodality.zmei.fhir.resource.terminology.ConceptMap();
    termxWebUrl.ifPresent(url -> toFhirWebSourceExtension(url, mapSet.getId()));
    fhirConceptMap.setId(toFhirId(mapSet, version));
    fhirConceptMap.setUrl(mapSet.getUri());
    fhirConceptMap.setPublisher(mapSet.getPublisher());
    fhirConceptMap.setName(mapSet.getName());
    fhirConceptMap.setTitle(toFhirName(mapSet.getTitle(), version.getPreferredLanguage()));
    fhirConceptMap.setDescription(toFhirName(mapSet.getDescription(), version.getPreferredLanguage()));
    fhirConceptMap.setPurpose(toFhirName(mapSet.getPurpose(), version.getPreferredLanguage()));
    fhirConceptMap.setText(toFhirText(mapSet.getNarrative()));
    fhirConceptMap.setExperimental(mapSet.getExperimental() != null && mapSet.getExperimental());
    fhirConceptMap.setIdentifier(toFhirIdentifiers(mapSet.getIdentifiers()));
    fhirConceptMap.setContact(toFhirContacts(mapSet.getContacts()));
    fhirConceptMap.setLastReviewDate(toFhirDate(provenances, "reviewed"));
    fhirConceptMap.setApprovalDate(toFhirDate(provenances, "approved"));
    fhirConceptMap.setCopyright(mapSet.getCopyright() != null ? mapSet.getCopyright().getHolder() : null);
    fhirConceptMap.setCopyrightLabel(mapSet.getCopyright() != null ? mapSet.getCopyright().getStatement() : null);
    fhirConceptMap.setJurisdiction(mapSet.getCopyright() != null && mapSet.getCopyright().getJurisdiction() != null ?
        List.of(new CodeableConcept().setText(mapSet.getCopyright().getJurisdiction())) : null);

    fhirConceptMap.setVersion(version.getVersion());
    fhirConceptMap.setDate(OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC));
    fhirConceptMap.setStatus(version.getStatus());
    fhirConceptMap.setSourceScopeUri(version.getScope().getSourceValueSet() == null ? null : version.getScope().getSourceValueSet().getUri());
    fhirConceptMap.setTargetScopeUri(version.getScope().getTargetValueSet() == null ? null : version.getScope().getTargetValueSet().getUri());
    fhirConceptMap.setGroup(toFhirGroup(version.getAssociations(), version.getScope()));
    return fhirConceptMap;
  }

  private List<ConceptMapGroup> toFhirGroup(List<MapSetAssociation> associations, MapSetVersionScope scope) {
    if (associations == null) {
      return new ArrayList<>();
    }
    List<MapSetResourceReference> allCS = new ArrayList<>();
    allCS.addAll(scope.getSourceCodeSystems() != null ? scope.getSourceCodeSystems() : List.of());
    allCS.addAll(scope.getTargetCodeSystems() != null ? scope.getTargetCodeSystems() : List.of());

    Map<String, String> csUri = associations.stream()
        .flatMap(a -> Stream.of(a.getSource().getCodeSystem(), a.getTarget() == null ? null : a.getTarget().getCodeSystem()).filter(Objects::nonNull))
        .distinct().map(cs -> {
          String uri = allCS.stream().filter(scs -> scs.getId().equals(cs)).findFirst().map(MapSetResourceReference::getUri).orElse(null);
          uri = uri == null ? codeSystemService.load(cs).map(CodeSystem::getUri).orElse(null) : uri;
          return Pair.of(cs, uri);
        }).filter(p -> p.getKey() != null && p.getValue() != null).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    Map<String, List<MapSetAssociation>> groups = associations.stream()
        .collect(Collectors.groupingBy(a -> a.getSource().getCodeSystem() + a.getTarget().getCodeSystem()));
    return groups.values().stream().map(a -> {
      ConceptMapGroup group = new ConceptMapGroup();
      String scs = a.get(0).getSource().getCodeSystem();
      String tcs = a.get(0).getTarget().getCodeSystem();
      group.setSource(scs == null ? null : csUri.getOrDefault(scs, scs));
      group.setTarget(tcs == null ? null : csUri.getOrDefault(tcs, tcs));
      Map<String, List<MapSetAssociation>> elements = a.stream().collect(Collectors.groupingBy(el -> el.getSource().getCode()));
      group.setElement(elements.values().stream().map(el -> new ConceptMapGroupElement()
              .setCode(el.get(0).getSource().getCode())
              .setDisplay(el.get(0).getSource().getDisplay())
              .setNoMap(el.get(0).isNoMap() ? true : null)
              .setTarget(el.stream().map(t -> new ConceptMapGroupElementTarget()
                  .setCode(t.getTarget().getCode())
                  .setDisplay(t.getTarget().getDisplay())
                  .setRelationship(t.getRelationship())).toList()))
          .collect(Collectors.toList()));
      return group;
    }).collect(Collectors.toList());
  }

  private static String addTranslationExtensions(String fhirJson, MapSet ms, MapSetVersion msv) {
    Map<String, Object> fhirMs = JsonUtil.toMap(fhirJson);
    Extension titleExtension = toFhirTranslationExtension(ms.getTitle(), msv.getPreferredLanguage());
    if (titleExtension != null) {
      fhirMs.put("_title", titleExtension);
    }
    Extension descriptionExtension = toFhirTranslationExtension(ms.getDescription(), msv.getPreferredLanguage());
    if (descriptionExtension != null) {
      fhirMs.put("_description", descriptionExtension);
    }
    Extension purposeExtension = toFhirTranslationExtension(ms.getPurpose(), msv.getPreferredLanguage());
    if (purposeExtension != null) {
      fhirMs.put("_purpose", purposeExtension);
    }
    return JsonUtil.toJson(fhirMs);
  }

  // -------------- FROM FHIR --------------

  public MapSet fromFhir(ConceptMap conceptMap) {
    MapSet ms = new MapSet();
    ms.setId(ConceptMapFhirMapper.parseCompositeId(conceptMap.getId())[0]);
    ms.setUri(conceptMap.getUrl());
    ms.setPublisher(conceptMap.getPublisher());
    ms.setName(conceptMap.getName());
    ms.setTitle(fromFhirName(conceptMap.getTitle(), conceptMap.getLanguage()));
    ms.setDescription(fromFhirName(conceptMap.getDescription(), conceptMap.getLanguage()));
    ms.setPurpose(fromFhirName(conceptMap.getPurpose(), conceptMap.getLanguage()));
    ms.setNarrative(conceptMap.getText() == null ? null : conceptMap.getText().getDiv());
    ms.setExperimental(conceptMap.getExperimental());
    ms.setIdentifiers(fromFhirIdentifiers(conceptMap.getIdentifier()));
    ms.setContacts(fromFhirContacts(conceptMap.getContact()));
    ms.setCopyright(new Copyright().setHolder(conceptMap.getCopyright()).setStatement(conceptMap.getCopyrightLabel()));

    ms.setVersions(List.of(fromFhirVersion(conceptMap)));
    return ms;
  }

  private MapSetVersion fromFhirVersion(ConceptMap conceptMap) {
    MapSetVersion version = new MapSetVersion();
    version.setMapSet(conceptMap.getId());
    version.setVersion(conceptMap.getVersion());
    version.setStatus(PublicationStatus.draft);
    version.setPreferredLanguage(conceptMap.getLanguage() == null ? Language.en : conceptMap.getLanguage());
    version.setAlgorithm(conceptMap.getVersionAlgorithmString());
    version.setReleaseDate(conceptMap.getDate() == null ? LocalDate.now() : LocalDate.from(conceptMap.getDate()));
    version.setScope(fromFhirScope(conceptMap));
    version.setAssociations(fromFhirAssociations(conceptMap));
    return version;
  }

  private MapSetVersionScope fromFhirScope(ConceptMap conceptMap) {
    MapSetVersionScope scope = new MapSetVersionScope();
    if (conceptMap.getSourceScopeUri() != null) {
      ValueSetVersion svsv = valueSetVersionService.loadLastVersionByUri(conceptMap.getSourceScopeUri());
      if (svsv != null) {
        scope.setSourceType("value-set");
        scope.setSourceValueSet(new MapSetResourceReference().setId(svsv.getValueSet()).setVersion(svsv.getVersion()));
      } else {
        scope.setSourceType("external-canonical-uri");
        scope.setSourceValueSet(new MapSetResourceReference().setUri(conceptMap.getSourceScopeUri()));
      }
    }
    if (conceptMap.getTargetScopeUri() != null) {
      ValueSetVersion tvsv = valueSetVersionService.loadLastVersionByUri(conceptMap.getTargetScopeUri());
      if (tvsv != null) {
        scope.setTargetType("value-set");
        scope.setTargetValueSet(new MapSetResourceReference().setId(tvsv.getValueSet()).setVersion(tvsv.getVersion()));
      } else {
        scope.setTargetType("external-canonical-uri");
        scope.setTargetValueSet(new MapSetResourceReference().setUri(conceptMap.getTargetScopeUri()));
      }
    }
    if (scope.getSourceType() == null) {
      List<MapSetResourceReference> cs = Optional.ofNullable(conceptMap.getGroup()).orElse(List.of()).stream()
          .map(ConceptMapGroup::getSource).distinct().map(codeSystemVersionService::loadLastVersionByUri).filter(Objects::nonNull)
          .map(v -> new MapSetResourceReference().setId(v.getCodeSystem()).setVersion(v.getVersion())).toList();
      scope.setSourceType("code-system");
      scope.setSourceCodeSystems(cs);
    }
    if (scope.getTargetType() == null) {
      List<MapSetResourceReference> cs = Optional.ofNullable(conceptMap.getGroup()).orElse(List.of()).stream()
          .map(ConceptMapGroup::getTarget).distinct().map(codeSystemVersionService::loadLastVersionByUri).filter(Objects::nonNull)
          .map(v -> new MapSetResourceReference().setId(v.getCodeSystem()).setVersion(v.getVersion())).toList();
      scope.setTargetType("code-system");
      scope.setTargetCodeSystems(cs);
    }
    return scope;
  }

  private List<MapSetAssociation> fromFhirAssociations(ConceptMap conceptMap) {
    List<MapSetAssociation> associations = new ArrayList<>();
    if (CollectionUtils.isEmpty(conceptMap.getGroup())) {
      return associations;
    }

    List<String> csUri = conceptMap.getGroup().stream().flatMap(g -> Stream.of(g.getSource(), g.getTarget())).filter(Objects::nonNull).distinct().toList();
    CodeSystemQueryParams params = new CodeSystemQueryParams().setUri(String.join(",", csUri)).limit(csUri.size());
    Map<String, String> codeSystems = codeSystemService.query(params).getData().stream().collect(Collectors.toMap(CodeSystem::getUri, CodeSystem::getId));

    conceptMap.getGroup().forEach(g -> g.getElement().forEach(s -> {
      if (s.getNoMap() != null && s.getNoMap()) {
        MapSetAssociation a = new MapSetAssociation();
        a.setSource(new MapSetAssociationEntity().setCode(s.getCode()).setDisplay(s.getDisplay())
            .setCodeSystem(g.getSource() == null ? null : codeSystems.getOrDefault(g.getSource(), g.getSource())));
        associations.add(a);
      }
      Optional.ofNullable(s.getTarget()).orElse(List.of()).forEach(t -> {
        MapSetAssociation a = new MapSetAssociation();
        a.setSource(new MapSetAssociationEntity().setCode(s.getCode()).setDisplay(s.getDisplay())
            .setCodeSystem(g.getSource() == null ? null : codeSystems.getOrDefault(g.getSource(), g.getSource())));
        a.setTarget(new MapSetAssociationEntity().setCode(t.getCode()).setDisplay(t.getDisplay())
            .setCodeSystem(g.getTarget() == null ? null : codeSystems.getOrDefault(g.getTarget(), g.getTarget())));
        a.setRelationship(t.getRelationship());
        associations.add(a);
      });
    }));
    return associations;
  }

  public List<AssociationType> fromFhirAssociationTypes(ConceptMap conceptMap) {
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
}
