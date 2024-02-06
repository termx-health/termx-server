package com.kodality.termx.terminology.fhir.conceptmap;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.model.LocalizedName;
import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.fhir.BaseFhirMapper;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.ts.Copyright;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationKind;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociation.MapSetAssociationEntity;
import com.kodality.termx.ts.mapset.MapSetProperty;
import com.kodality.termx.ts.mapset.MapSetPropertyValue;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetResourceReference;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetVersionScope;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroup;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElement;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElementTarget;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElementTargetProperty;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapProperty;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.CollectionUtils;
import java.math.BigDecimal;
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
  private final ValueSetVersionService valueSetVersionService;

  private static Optional<String> termxWebUrl;

  public ConceptMapFhirMapper(CodeSystemService codeSystemService, CodeSystemVersionService codeSystemVersionService,
                              ValueSetVersionService valueSetVersionService,
                              @Value("${termx.web-url}") Optional<String> termxWebUrl) {
    this.codeSystemService = codeSystemService;
    this.codeSystemVersionService = codeSystemVersionService;
    this.valueSetVersionService = valueSetVersionService;
    ConceptMapFhirMapper.termxWebUrl = termxWebUrl;
  }

  // -------------- TO FHIR --------------

  public static String toFhirId(MapSet mapSet, MapSetVersion version) {
    return mapSet.getId() + BaseFhirMapper.SEPARATOR + version.getVersion();
  }

  public String toFhirJson(MapSet ms, MapSetVersion msv, List<Provenance> provenances) {
    return FhirMapper.toJson(toFhir(ms, msv, provenances));
  }

  public com.kodality.zmei.fhir.resource.terminology.ConceptMap toFhir(MapSet mapSet, MapSetVersion version, List<Provenance> provenances) {
    com.kodality.zmei.fhir.resource.terminology.ConceptMap fhirConceptMap = new com.kodality.zmei.fhir.resource.terminology.ConceptMap();
    termxWebUrl.ifPresent(url -> toFhirWebSourceExtension(url, mapSet.getId()));
    fhirConceptMap.setId(toFhirId(mapSet, version));
    fhirConceptMap.setUrl(mapSet.getUri());
    fhirConceptMap.setPublisher(mapSet.getPublisher());
    fhirConceptMap.setName(mapSet.getName());
    fhirConceptMap.setTitle(toFhirName(mapSet.getTitle(), version.getPreferredLanguage()));
    fhirConceptMap.setPrimitiveExtensions("title", toFhirTranslationExtension(mapSet.getTitle(), version.getPreferredLanguage()));
    LocalizedName description = joinDescriptions(mapSet.getDescription(), version.getDescription());
    fhirConceptMap.setDescription(toFhirName(description, version.getPreferredLanguage()));
    fhirConceptMap.setPrimitiveExtensions("description", toFhirTranslationExtension(description, version.getPreferredLanguage()));
    fhirConceptMap.setPurpose(toFhirName(mapSet.getPurpose(), version.getPreferredLanguage()));
    fhirConceptMap.setPrimitiveExtensions("purpose", toFhirTranslationExtension(mapSet.getPurpose(), version.getPreferredLanguage()));
    fhirConceptMap.setText(toFhirText(mapSet.getNarrative()));
    fhirConceptMap.setExperimental(mapSet.getExperimental() != null && mapSet.getExperimental());
    fhirConceptMap.setIdentifier(toFhirIdentifiers(mapSet.getIdentifiers(), version.getIdentifiers()));
    fhirConceptMap.setContact(toFhirContacts(mapSet.getContacts()));
    fhirConceptMap.setDate(toFhirOffsetDateTime(provenances));
    fhirConceptMap.setLastReviewDate(toFhirDate(provenances, "reviewed"));
    fhirConceptMap.setApprovalDate(toFhirDate(provenances, "approved"));
    fhirConceptMap.setCopyright(mapSet.getCopyright() != null ? mapSet.getCopyright().getHolder() : null);
    fhirConceptMap.setCopyrightLabel(mapSet.getCopyright() != null ? mapSet.getCopyright().getStatement() : null);
    fhirConceptMap.setJurisdiction(mapSet.getCopyright() != null && mapSet.getCopyright().getJurisdiction() != null ?
        List.of(new CodeableConcept().setText(mapSet.getCopyright().getJurisdiction())) : null);

    fhirConceptMap.setVersion(version.getVersion());
    fhirConceptMap.setEffectivePeriod(new Period(
        OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC),
        version.getExpirationDate() == null ? null : OffsetDateTime.of(version.getReleaseDate().atTime(23, 59), ZoneOffset.UTC)));
    fhirConceptMap.setStatus(version.getStatus());
    fhirConceptMap.setSourceScopeUri(version.getScope().getSourceValueSet() == null ? null : version.getScope().getSourceValueSet().getUri());
    fhirConceptMap.setTargetScopeUri(version.getScope().getTargetValueSet() == null ? null : version.getScope().getTargetValueSet().getUri());
    fhirConceptMap.setGroup(toFhirGroup(version.getAssociations(), version.getScope()));
    fhirConceptMap.setProperty(toFhirProperties(mapSet.getProperties(), version.getPreferredLanguage()));
    return fhirConceptMap;
  }

  private List<ConceptMapProperty> toFhirProperties(List<MapSetProperty> properties, String lang) {
    if (properties == null) {
      return new ArrayList<>();
    }
    return properties.stream().map(p -> new ConceptMapProperty()
        .setCode(p.getName())
        .setUri(p.getUri())
        .setType(p.getType())
        .setDescription(toFhirName(p.getDescription(), lang))
    ).toList();
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
                  .setRelationship(t.getRelationship())
                  .setProperty(toFhirPropertyValues(t.getPropertyValues()))).toList()))
          .collect(Collectors.toList()));
      return group;
    }).collect(Collectors.toList());
  }

  private List<ConceptMapGroupElementTargetProperty> toFhirPropertyValues(List<MapSetPropertyValue> propertyValues) {
    if (propertyValues == null) {
      return new ArrayList<>();
    }
    return propertyValues.stream().map(pv -> {
      ConceptMapGroupElementTargetProperty fhir = new ConceptMapGroupElementTargetProperty().setCode(pv.getMapSetPropertyName());
      switch (pv.getMapSetPropertyType()) {
        case EntityPropertyType.code -> fhir.setValueCode((String) pv.getValue());
        case EntityPropertyType.string -> fhir.setValueString((String) pv.getValue());
        case EntityPropertyType.bool -> fhir.setValueBoolean((Boolean) pv.getValue());
        case EntityPropertyType.decimal -> fhir.setValueDecimal(new BigDecimal(String.valueOf(pv.getValue())));
        case EntityPropertyType.integer -> fhir.setValueInteger(Integer.valueOf(String.valueOf(pv.getValue())));
        case EntityPropertyType.coding -> {
          Concept concept = JsonUtil.getObjectMapper().convertValue(pv.getValue(), Concept.class);
          fhir.setValueCoding(new Coding(concept.getCodeSystem(), concept.getCode()));
        }
        case EntityPropertyType.dateTime -> {
          if (pv.getValue() instanceof OffsetDateTime) {
            fhir.setValueDateTime((OffsetDateTime) pv.getValue());
          } else {
            fhir.setValueDateTime(DateUtil.parseOffsetDateTime((String) pv.getValue()));
          }
        }
      }
      return fhir;
    }).toList();
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
    ms.setProperties(fromFhirProperties(conceptMap.getProperty(), conceptMap.getLanguage()));

    ms.setVersions(List.of(fromFhirVersion(conceptMap)));
    return ms;
  }

  private List<MapSetProperty> fromFhirProperties(List<ConceptMapProperty> properties, String lang) {
    if (properties == null) {
      return new ArrayList<>();
    }
    return properties.stream().map(p -> {
      MapSetProperty property = new MapSetProperty();
      property.setDescription(fromFhirName(p.getDescription(), lang));
      property.setStatus(PublicationStatus.active);
      property.setName(p.getCode());
      property.setUri(p.getUri());
      property.setType(p.getType());
      return property;
    }).toList();
  }

  private MapSetVersion fromFhirVersion(ConceptMap conceptMap) {
    MapSetVersion version = new MapSetVersion();
    version.setMapSet(conceptMap.getId());
    version.setVersion(conceptMap.getVersion());
    version.setStatus(PublicationStatus.draft);
    version.setPreferredLanguage(conceptMap.getLanguage() == null ? Language.en : conceptMap.getLanguage());
    version.setAlgorithm(conceptMap.getVersionAlgorithmString());
    version.setReleaseDate(conceptMap.getEffectivePeriod() == null || conceptMap.getEffectivePeriod().getStart() == null ? LocalDate.now() :
        LocalDate.from(conceptMap.getEffectivePeriod().getStart()));
    version.setExpirationDate(conceptMap.getEffectivePeriod() == null || conceptMap.getEffectivePeriod().getEnd() == null ? null :
        LocalDate.from(conceptMap.getEffectivePeriod().getEnd()));
    version.setScope(fromFhirScope(conceptMap));
    version.setAssociations(fromFhirAssociations(conceptMap));
    version.setIdentifiers(fromFhirVersionIdentifiers(conceptMap.getIdentifier()));
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
        a.setPropertyValues(fromFhirPropertyValues(t.getProperty()));
        associations.add(a);
      });
    }));
    return associations;
  }

  private List<MapSetPropertyValue> fromFhirPropertyValues(List<ConceptMapGroupElementTargetProperty> propertyValues) {
    if (propertyValues == null) {
      return new ArrayList<>();
    }
    return propertyValues.stream().map(pv -> new MapSetPropertyValue()
        .setMapSetPropertyName(pv.getCode())
        .setValue(Stream.of(pv.getValueCode(), pv.getValueCoding(),
            pv.getValueString(), pv.getValueInteger(),
            pv.getValueBoolean(), pv.getValueDateTime(), pv.getValueDecimal()
        ).filter(Objects::nonNull).findFirst().orElse(null))).toList();
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

  public static MapSetQueryParams fromFhir(SearchCriterion fhir) {
    MapSetQueryParams params = new MapSetQueryParams();
    getSimpleParams(fhir).forEach((k, v) -> {
      switch (k) {
        case SearchCriterion._COUNT -> params.setLimit(fhir.getCount());
        case SearchCriterion._PAGE -> params.setOffset(getOffset(fhir));
        case "_id" -> params.setIds(ConceptMapFhirMapper.parseCompositeId(v)[0]);
        // https://www.hl7.org/fhir/conceptmap-search.html#4.10.26
        case "date" -> {
          if (v.startsWith("ge")) {
            params.setVersionReleaseDateGe(LocalDate.parse(v.substring(2)));
          } else {
            params.setVersionReleaseDate(LocalDate.parse(v));
          }
        }
        // https://www.hl7.org/fhir/conceptmap-search.html#4.10.28 :string
        case "description:exact" -> params.setDescription(v);
        case "description" -> params.setDescriptionStarts(v);
        case "description:contains" -> params.setDescriptionContains(v);
        // https://www.hl7.org/fhir/conceptmap-search.html#4.10.30 :token
        case "identifier" -> params.setIdentifier(v);
        // https://www.hl7.org/fhir/conceptmap-search.html#4.10.33 :string
        case "name:exact" -> params.setName(v);
        case "name" -> params.setNameStarts(v);
        case "name:contains" -> params.setNameContains(v);
        // https://www.hl7.org/fhir/conceptmap-search.html#4.10.46 :string
        case "title:exact" -> params.setTitle(v);
        case "title" -> params.setTitleStarts(v);
        case "title:contains" -> params.setTitleContains(v);
        // https://www.hl7.org/fhir/conceptmap-search.html#4.10.36 :string
        case "publisher:exact" -> params.setPublisher(v);
        case "publisher" -> params.setPublisherStarts(v);
        case "publisher:contains" -> params.setPublisherContains(v);
        // https://www.hl7.org/fhir/conceptmap-search.html#4.10.41 :token
        case "status" -> params.setVersionStatus(v);
        // https://www.hl7.org/fhir/conceptmap-search.html#4.10.37 :token
        case "source-code" -> params.setVersionConceptSourceCode(v);
        // https://www.hl7.org/fhir/conceptmap-search.html#4.10.42 :token
        case "target-code" -> params.setVersionConceptTargetCode(v);
        // https://www.hl7.org/fhir/conceptmap-search.html#4.10.48 :uri
        case "url" -> params.setUri(v);
        // https://www.hl7.org/fhir/conceptmap-search.html#4.10.49 :token
        case "version" -> params.setVersionVersion(v);

        default -> throw new ApiClientException("Search by '" + k + "' not supported");
      }
    });
    params.setVersionsDecorated(true);
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.MS_VIEW));
    return params;
  }
}
