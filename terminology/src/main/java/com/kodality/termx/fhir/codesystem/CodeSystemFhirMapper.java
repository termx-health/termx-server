package com.kodality.termx.fhir.codesystem;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termx.fhir.BaseFhirMapper;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.Copyright;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyKind;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptDesignation;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConceptProperty;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemProperty;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Slf4j
@Context
public class CodeSystemFhirMapper extends BaseFhirMapper {
  private static final String DISPLAY = "display";
  private static final String DEFINITION = "definition";
  private static Optional<String> termxWebUrl;
  public CodeSystemFhirMapper(@Value("${termx.web-url}") Optional<String> termxWebUrl) {
    CodeSystemFhirMapper.termxWebUrl = termxWebUrl;
  }

  // -------------- TO FHIR --------------

  public static String toFhirId(CodeSystem cs, CodeSystemVersion csv) {
    return cs.getId() + "@" + csv.getVersion();
  }

  public static String toFhirJson(CodeSystem cs, CodeSystemVersion csv, List<Provenance> provenances) {
    return addTranslationExtensions(FhirMapper.toJson(toFhir(cs, csv, provenances)), cs, csv);
  }

  public static com.kodality.zmei.fhir.resource.terminology.CodeSystem toFhir(CodeSystem codeSystem, CodeSystemVersion version, List<Provenance> provenances) {
    com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem = new com.kodality.zmei.fhir.resource.terminology.CodeSystem();
    termxWebUrl.ifPresent(url -> toFhirWebSourceExtension(url, codeSystem.getId()));
    fhirCodeSystem.setId(toFhirId(codeSystem, version));
    fhirCodeSystem.setUrl(codeSystem.getUri());
    fhirCodeSystem.setPublisher(codeSystem.getPublisher());
    fhirCodeSystem.setName(codeSystem.getName());
    fhirCodeSystem.setTitle(toFhirName(codeSystem.getTitle(), version.getPreferredLanguage()));
    fhirCodeSystem.setDescription(toFhirName(codeSystem.getDescription(), version.getPreferredLanguage()));
    fhirCodeSystem.setPurpose(toFhirName(codeSystem.getPurpose(), version.getPreferredLanguage()));
    fhirCodeSystem.setText(toFhirText(codeSystem.getNarrative()));
    fhirCodeSystem.setExperimental(codeSystem.getExperimental() != null && codeSystem.getExperimental());
    fhirCodeSystem.setIdentifier(toFhirIdentifiers(codeSystem.getIdentifiers()));
    fhirCodeSystem.setContact(toFhirContacts(codeSystem.getContacts()));
    fhirCodeSystem.setLastReviewDate(toFhirDate(provenances, "reviewed"));
    fhirCodeSystem.setApprovalDate(toFhirDate(provenances, "approved"));
    fhirCodeSystem.setCopyright(codeSystem.getCopyright() != null ? codeSystem.getCopyright().getHolder() : null);
    fhirCodeSystem.setCopyrightLabel(codeSystem.getCopyright() != null ? codeSystem.getCopyright().getStatement() : null);
    fhirCodeSystem.setJurisdiction(codeSystem.getCopyright() != null && codeSystem.getCopyright().getJurisdiction() != null  ? List.of(new CodeableConcept().setText(codeSystem.getCopyright().getJurisdiction())) : null);
    fhirCodeSystem.setHierarchyMeaning(codeSystem.getHierarchyMeaning());
    fhirCodeSystem.setContent(codeSystem.getContent());
    fhirCodeSystem.setCaseSensitive(codeSystem.getCaseSensitive() != null && !CaseSignificance.entire_term_case_insensitive.equals(codeSystem.getCaseSensitive()));
    fhirCodeSystem.setSupplements(codeSystem.getBaseCodeSystemUri());

    fhirCodeSystem.setVersion(version.getVersion());
    fhirCodeSystem.setLanguage(version.getPreferredLanguage());
    fhirCodeSystem.setVersionAlgorithmString(version.getAlgorithm());
    fhirCodeSystem.setDate(OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC));
    fhirCodeSystem.setStatus(version.getStatus());
    fhirCodeSystem.setProperty(toFhirCodeSystemProperty(codeSystem.getProperties(), version.getPreferredLanguage()));

    List<Pair<CodeSystemAssociation, CodeSystemEntityVersion>> entitiesWithAssociations =
        version.getEntities().stream().filter(e -> e.getAssociations() != null).flatMap(e -> e.getAssociations().stream().map(a -> Pair.of(a, e))).toList();
    Map<Long, List<CodeSystemEntityVersion>> entities =
        entitiesWithAssociations.stream().collect(Collectors.groupingBy(e -> e.getKey().getTargetId(), mapping(Pair::getValue, toList())));

    fhirCodeSystem.setConcept(version.getEntities().stream()
        .filter(e -> CollectionUtils.isEmpty(e.getAssociations()))
        .map(e -> toFhir(e, codeSystem, version, entities))
        .sorted(Comparator.comparing(CodeSystemConcept::getCode))
        .collect(Collectors.toList()));
    return fhirCodeSystem;
  }

  private static CodeSystemConcept toFhir(CodeSystemEntityVersion e, CodeSystem codeSystem, CodeSystemVersion version,
                                          Map<Long, List<CodeSystemEntityVersion>> entities) {
    CodeSystemConcept concept = new CodeSystemConcept();
    concept.setCode(e.getCode());
    setDesignations(concept, e.getDesignations(), version.getPreferredLanguage());
    concept.setProperty(toFhirConceptProperties(e.getPropertyValues(), codeSystem.getProperties()));
    concept.setConcept(toFhirConcepts(entities, e.getId(), codeSystem, version));
    return concept;
  }

  private static void setDesignations(CodeSystemConcept concept, List<Designation> designations, String preferredLanguage) {
    if (designations == null) {
      return;
    }
    Designation display = designations.stream()
        .filter(d -> d.getDesignationType().equals("display") && (preferredLanguage == null || preferredLanguage.equals(d.getLanguage())))
        .findFirst().orElse(null);
    Designation definition = designations.stream()
        .filter(d -> d.getDesignationType().equals("definition") && (preferredLanguage == null || preferredLanguage.equals(d.getLanguage())))
        .findFirst().orElse(null);
    concept.setDisplay(display != null ? display.getName() : null);
    concept.setDefinition(definition != null ? definition.getName() : null);
    concept.setDesignation(toFhirDesignations(designations.stream()
        .filter(d -> (display == null || !display.getId().equals(d.getId()) && (definition == null || !definition.getId().equals(d.getId())))).toList()));
  }

  private static List<CodeSystemConceptDesignation> toFhirDesignations(List<Designation> designations) {
    if (CollectionUtils.isEmpty(designations)) {
      return null;
    }
    return designations.stream().map(d -> new CodeSystemConceptDesignation()
            .setLanguage(d.getLanguage())
            .setValue(d.getName())
            .setUse(new Coding(d.getDesignationType())))
        .sorted(Comparator.comparing(d -> d.getLanguage() == null ? "" : d.getLanguage()))
        .sorted(Comparator.comparing(d -> d.getUse().getCode()))
        .toList();
  }

  private static List<CodeSystemProperty> toFhirCodeSystemProperty(List<EntityProperty> entityProperties, String lang) {
    if (CollectionUtils.isEmpty(entityProperties)) {
      return List.of();
    }
    return entityProperties.stream().map(p ->
        new CodeSystemProperty()
            .setCode(p.getName())
            .setType(p.getType())
            .setUri(p.getUri())
            .setDescription(toFhirName(p.getDescription(), lang))
    ).sorted(Comparator.comparing(CodeSystemProperty::getCode)).toList();
  }

  private static List<CodeSystemConceptProperty> toFhirConceptProperties(List<EntityPropertyValue> propertyValues, List<EntityProperty> properties) {
    if (CollectionUtils.isEmpty(propertyValues)) {
      return null;
    }
    Map<Long, EntityProperty> entityProperties = properties.stream().collect(Collectors.toMap(ep -> ep.getId(), ep -> ep));
    return propertyValues.stream().map(pv -> {
      EntityProperty entityProperty = entityProperties.get(pv.getEntityPropertyId());
      CodeSystemConceptProperty fhir = new CodeSystemConceptProperty();
      fhir.setCode(entityProperty.getName());
      switch (entityProperty.getType()) {
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
    }).sorted(Comparator.comparing(CodeSystemConceptProperty::getCode)).toList();
  }

  private static List<CodeSystemConcept> toFhirConcepts(Map<Long, List<CodeSystemEntityVersion>> entities, Long targetId, CodeSystem codeSystem,
                                                        CodeSystemVersion version) {
    List<CodeSystemConcept> result = entities.getOrDefault(targetId, List.of()).stream().map(e -> toFhir(e, codeSystem, version, entities)).collect(Collectors.toList());
    return CollectionUtils.isEmpty(result) ? null : result.stream().sorted(Comparator.comparing(CodeSystemConcept::getCode)).toList();
  }

  private static String addTranslationExtensions(String fhirJson, CodeSystem cs, CodeSystemVersion csv) {
    Map<String, Object> fhirCs = JsonUtil.toMap(fhirJson);
    Extension titleExtension = toFhirTranslationExtension(cs.getTitle(), csv.getPreferredLanguage());
    if (titleExtension != null) {
      fhirCs.put("_title", titleExtension);
    }
    Extension descriptionExtension = toFhirTranslationExtension(cs.getDescription(), csv.getPreferredLanguage());
    if (descriptionExtension != null) {
      fhirCs.put("_description", descriptionExtension);
    }
    Extension purposeExtension = toFhirTranslationExtension(cs.getPurpose(), csv.getPreferredLanguage());
    if (purposeExtension != null) {
      fhirCs.put("_purpose", purposeExtension);
    }
    return JsonUtil.toJson(fhirCs);
  }

  // -------------- FROM FHIR --------------

  public static CodeSystem fromFhirCodeSystem(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setId(CodeSystemFhirMapper.parseCompositeId(fhirCodeSystem.getId())[0]);
    codeSystem.setUri(fhirCodeSystem.getUrl());
    codeSystem.setPublisher(fhirCodeSystem.getPublisher());
    codeSystem.setName(fhirCodeSystem.getName());
    codeSystem.setTitle(fromFhirName(fhirCodeSystem.getTitle(), fhirCodeSystem.getLanguage()));
    codeSystem.setDescription(fromFhirName(fhirCodeSystem.getDescription(), fhirCodeSystem.getLanguage()));
    codeSystem.setPurpose(fromFhirName(fhirCodeSystem.getPurpose(), fhirCodeSystem.getLanguage()));
    codeSystem.setNarrative(fhirCodeSystem.getText() == null ? null : fhirCodeSystem.getText().getDiv());
    codeSystem.setExperimental(fhirCodeSystem.getExperimental());
    codeSystem.setIdentifiers(fromFhirIdentifiers(fhirCodeSystem.getIdentifier()));
    codeSystem.setContacts(fromFhirContacts(fhirCodeSystem.getContact()));
    codeSystem.setCopyright(new Copyright().setHolder(fhirCodeSystem.getCopyright()).setStatement(fhirCodeSystem.getCopyrightLabel()));
    codeSystem.setHierarchyMeaning(fhirCodeSystem.getHierarchyMeaning());
    codeSystem.setContent(fhirCodeSystem.getContent());
    codeSystem.setCaseSensitive(fhirCodeSystem.getCaseSensitive() != null && fhirCodeSystem.getCaseSensitive() ? CaseSignificance.entire_term_case_sensitive :
        CaseSignificance.entire_term_case_insensitive);

    codeSystem.setVersions(fromFhirVersion(fhirCodeSystem));
    codeSystem.setConcepts(fromFhirConcepts(fhirCodeSystem.getConcept(), fhirCodeSystem, null));
    codeSystem.setProperties(fromFhirProperties(fhirCodeSystem));
    return codeSystem;
  }

  private static List<CodeSystemVersion> fromFhirVersion(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    CodeSystemVersion version = new CodeSystemVersion();
    version.setCodeSystem(fhirCodeSystem.getId());
    version.setVersion(fhirCodeSystem.getVersion() == null ? "1.0.0" : fhirCodeSystem.getVersion());
    version.setPreferredLanguage(fhirCodeSystem.getLanguage() == null ? Language.en : fhirCodeSystem.getLanguage());
    version.setSupportedLanguages(Optional.ofNullable(fhirCodeSystem.getConcept()).orElse(new ArrayList<>()).stream()
        .filter(c -> c.getDesignation() != null)
        .flatMap(c -> c.getDesignation().stream().map(CodeSystemConceptDesignation::getLanguage)).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
    if (!version.getSupportedLanguages().contains(version.getPreferredLanguage())) {
      version.getSupportedLanguages().add(version.getPreferredLanguage());
    }
    version.setStatus(PublicationStatus.draft);
    version.setAlgorithm(fhirCodeSystem.getVersionAlgorithmString());
    version.setReleaseDate(fhirCodeSystem.getDate() == null ? LocalDate.now() : LocalDate.from(fhirCodeSystem.getDate()));
    return List.of(version);
  }

  private static List<EntityProperty> fromFhirProperties(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    List<EntityProperty> defaultProperties = new ArrayList<>();

    EntityProperty display = new EntityProperty();
    display.setName(DISPLAY);
    display.setType(EntityPropertyType.string);
    display.setKind(EntityPropertyKind.designation);
    display.setStatus(PublicationStatus.active);

    EntityProperty definition = new EntityProperty();
    definition.setName(DEFINITION);
    definition.setType(EntityPropertyType.string);
    definition.setKind(EntityPropertyKind.designation);
    definition.setStatus(PublicationStatus.active);

    defaultProperties.add(display);
    defaultProperties.add(definition);
    if (fhirCodeSystem.getProperty() == null) {
      return defaultProperties;
    }

    List<EntityProperty> properties = fhirCodeSystem.getProperty().stream().map(p -> {
      EntityProperty property = new EntityProperty();
      property.setName(p.getCode());
      property.setUri(p.getUri());
      property.setDescription(fromFhirName(p.getDescription(), fhirCodeSystem.getLanguage()));
      property.setType(p.getType());
      property.setKind(EntityPropertyKind.property);
      property.setStatus(PublicationStatus.active);
      return property;
    }).collect(Collectors.toList());
    properties.addAll(defaultProperties);

    if (fhirCodeSystem.getConcept() != null) {
      List<EntityProperty> designationProperties = fhirCodeSystem.getConcept().stream()
          .filter(c -> c.getDesignation() != null)
          .flatMap(c -> c.getDesignation().stream())
          .filter(d -> d.getUse() != null && d.getUse().getCode() != null)
          .map(d -> {
            EntityProperty ep = new EntityProperty();
            ep.setName(d.getUse().getCode());
            ep.setType(EntityPropertyType.string);
            ep.setKind(EntityPropertyKind.designation);
            ep.setStatus(PublicationStatus.active);
            return ep;
          }).toList();
      properties.addAll(designationProperties);
    }
    return properties.stream().collect(Collectors.toMap(EntityProperty::getName, p -> p, (p, q) -> p)).values().stream().toList();
  }

  private static List<Concept> fromFhirConcepts(List<CodeSystemConcept> fhirConcepts,
                                                com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem, CodeSystemConcept parent) {
    List<Concept> concepts = new ArrayList<>();
    if (io.micronaut.core.util.CollectionUtils.isEmpty(fhirConcepts)) {
      return concepts;
    }
    fhirConcepts.forEach(c -> {
      Concept concept = new Concept();
      concept.setCode(c.getCode());
      concept.setCodeSystem(fhirCodeSystem.getId());
      concept.setVersions(fromFhirConcepts(c, fhirCodeSystem, parent));
      concepts.add(concept);
      if (c.getConcept() != null) {
        concepts.addAll(fromFhirConcepts(c.getConcept(), fhirCodeSystem, c));
      }
    });
    return concepts;
  }

  private static List<CodeSystemEntityVersion> fromFhirConcepts(CodeSystemConcept c,
                                                                com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem,
                                                                CodeSystemConcept parent) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(c.getCode());
    version.setCodeSystem(codeSystem.getId());
    version.setDesignations(fromFhirDesignations(c, codeSystem));
    version.setPropertyValues(fromFhirProperties(c.getProperty()));
    version.setAssociations(fromFhirAssociations(parent, codeSystem));
    version.setStatus(fromFhirStatus(c.getProperty()));
    return List.of(version);
  }

  private static String fromFhirStatus(List<CodeSystemConceptProperty> propertyValues) {
    if (propertyValues == null) {
      return null;
    }
    return propertyValues.stream().filter(pv -> "status".equals(pv.getCode())).findFirst().map(CodeSystemConceptProperty::getValueCode).orElse(null);
  }

  private static List<Designation> fromFhirDesignations(CodeSystemConcept c,
                                                        com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem) {
    String caseSignificance = codeSystem.getCaseSensitive() != null && codeSystem.getCaseSensitive() ? CaseSignificance.entire_term_case_sensitive : CaseSignificance.entire_term_case_insensitive;

    if (c.getDesignation() == null) {
      c.setDesignation(new ArrayList<>());
    }
    List<Designation> designations = c.getDesignation().stream().map(d -> {
      Designation designation = new Designation();
      designation.setDesignationType(d.getUse() == null ? DISPLAY : d.getUse().getCode());
      designation.setName(d.getValue());
      designation.setLanguage(d.getLanguage() == null ? Language.en : d.getLanguage());
      designation.setCaseSignificance(caseSignificance);
      designation.setDesignationKind("text");
      designation.setStatus("active");
      return designation;
    }).collect(Collectors.toList());

    Designation display = new Designation();
    display.setDesignationType(DISPLAY);
    display.setName(c.getDisplay());
    display.setPreferred(true);
    display.setLanguage(codeSystem.getLanguage() == null ? Language.en : codeSystem.getLanguage());
    display.setCaseSignificance(caseSignificance);
    display.setDesignationKind("text");
    display.setStatus("active");
    if (display.getName() != null && designations.stream().noneMatch(d -> isSameDesignation(d, display))) {
      designations.add(display);
    }

    if (c.getDefinition() != null) {
      Designation definition = new Designation();
      definition.setDesignationType(DEFINITION);
      definition.setName(c.getDefinition());
      definition.setLanguage(codeSystem.getLanguage() == null ? Language.en : codeSystem.getLanguage());
      definition.setCaseSignificance(caseSignificance);
      definition.setDesignationKind("text");
      definition.setStatus("active");
      if (definition.getName() != null && designations.stream().noneMatch(d -> isSameDesignation(d, definition))) {
        designations.add(definition);
      }
    }
    return designations;
  }

  private static boolean isSameDesignation(Designation d1, Designation d2) {
    return d1.getDesignationType().equals(d2.getDesignationType()) && d1.getName().equals(d2.getName()) && d1.getLanguage().equals(d2.getLanguage());
  }

  private static List<EntityPropertyValue> fromFhirProperties(List<CodeSystemConceptProperty> propertyValues) {
    if (propertyValues == null) {
      return new ArrayList<>();
    }
    return propertyValues.stream().map(v -> {
      EntityPropertyValue value = new EntityPropertyValue();
      value.setValue(Stream.of(
          v.getValueCode(), v.getValueCoding(),
          v.getValueString(), v.getValueInteger(),
          v.getValueBoolean(), v.getValueDateTime(), v.getValueDecimal()
      ).filter(Objects::nonNull).findFirst().orElse(null));
      value.setEntityProperty(v.getCode());
      return value;
    }).collect(Collectors.toList());
  }

  private static List<CodeSystemAssociation> fromFhirAssociations(CodeSystemConcept parent,
                                                                  com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem) {
    if (parent == null) {
      return new ArrayList<>();
    }
    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setAssociationType(codeSystem.getHierarchyMeaning() == null ? "is-a" : codeSystem.getHierarchyMeaning());
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(parent.getCode());
    association.setCodeSystem(codeSystem.getId());
    return List.of(association);
  }

  public static CodeSystemQueryParams fromFhir(SearchCriterion fhir) {
    CodeSystemQueryParams params = new CodeSystemQueryParams();
    getSimpleParams(fhir).forEach((k, v) -> {
      switch (k) {
        case SearchCriterion._COUNT -> params.setLimit(fhir.getCount());
        case SearchCriterion._PAGE -> params.setOffset(getOffset(fhir));
        case "_id" -> params.setIds(v);
        case "system", "url" -> params.setUri(v);
        case "version" -> params.setVersionVersion(v);
        case "title", "name" -> params.setNameContains(v);
        case "status" -> params.setVersionStatus(v);
        case "publisher" -> params.setPublisher(v);
        case "description" -> params.setDescriptionContains(v);
        case "content-mode" -> params.setContent(v);
        case "code" -> params.setConceptCode(v);
        default -> throw new ApiClientException("Search by '" + k + "' not supported");
      }
    });
    params.setVersionsDecorated(true);
    params.setPropertiesDecorated(true);
    return params;
  }
}
