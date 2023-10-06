package com.kodality.termx.fhir.codesystem;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.fhir.BaseFhirMapper;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.Copyright;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.Permissions;
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
import com.kodality.termx.ts.property.PropertyReference;
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
    return cs.getId() + BaseFhirMapper.SEPARATOR + csv.getVersion();
  }

  public static String toFhirJson(CodeSystem cs, CodeSystemVersion csv, List<Provenance> provenances) {
    return FhirMapper.toJson(toFhir(cs, csv, provenances));
  }

  public static com.kodality.zmei.fhir.resource.terminology.CodeSystem toFhir(CodeSystem codeSystem, CodeSystemVersion version, List<Provenance> provenances) {
    com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem = new com.kodality.zmei.fhir.resource.terminology.CodeSystem();
    termxWebUrl.ifPresent(url -> toFhirWebSourceExtension(url, codeSystem.getId()));
    fhirCodeSystem.setId(toFhirId(codeSystem, version));
    fhirCodeSystem.setUrl(codeSystem.getUri());
    fhirCodeSystem.setPublisher(codeSystem.getPublisher());
    fhirCodeSystem.setName(codeSystem.getName());
    fhirCodeSystem.setTitle(toFhirName(codeSystem.getTitle(), version.getPreferredLanguage()));
    fhirCodeSystem.setPrimitiveExtensions("title", toFhirTranslationExtension(codeSystem.getTitle(), version.getPreferredLanguage()));
    fhirCodeSystem.setDescription(toFhirName(codeSystem.getDescription(), version.getPreferredLanguage()));
    fhirCodeSystem.setPrimitiveExtensions("description", toFhirTranslationExtension(codeSystem.getDescription(), version.getPreferredLanguage()));
    fhirCodeSystem.setPurpose(toFhirName(codeSystem.getPurpose(), version.getPreferredLanguage()));
    fhirCodeSystem.setPrimitiveExtensions("purpose", toFhirTranslationExtension(codeSystem.getPurpose(), version.getPreferredLanguage()));
    fhirCodeSystem.setText(toFhirText(codeSystem.getNarrative()));
    fhirCodeSystem.setExperimental(codeSystem.getExperimental() != null && codeSystem.getExperimental());
    fhirCodeSystem.setIdentifier(toFhirIdentifiers(codeSystem.getIdentifiers()));
    fhirCodeSystem.setContact(toFhirContacts(codeSystem.getContacts()));
    fhirCodeSystem.setLastReviewDate(toFhirDate(provenances, "reviewed"));
    fhirCodeSystem.setApprovalDate(toFhirDate(provenances, "approved"));
    fhirCodeSystem.setHierarchyMeaning(codeSystem.getHierarchyMeaning());
    fhirCodeSystem.setContent(codeSystem.getContent());
    fhirCodeSystem.setCaseSensitive(
        codeSystem.getCaseSensitive() != null && !CaseSignificance.entire_term_case_insensitive.equals(codeSystem.getCaseSensitive()));
    fhirCodeSystem.setSupplements(codeSystem.getBaseCodeSystemUri());
    if (codeSystem.getCopyright() != null) {
      fhirCodeSystem.setCopyright(codeSystem.getCopyright().getHolder());
      fhirCodeSystem.setCopyrightLabel(codeSystem.getCopyright().getStatement());
      fhirCodeSystem.setJurisdiction(codeSystem.getCopyright().getJurisdiction() != null ?
          List.of(new CodeableConcept().setText(codeSystem.getCopyright().getJurisdiction())) : null);
    }
    if (codeSystem.getPermissions() != null) {
      fhirCodeSystem.setAuthor(codeSystem.getPermissions().getAdmin() != null ? toFhirContacts(codeSystem.getPermissions().getAdmin()) : null);
      fhirCodeSystem.setEditor(codeSystem.getPermissions().getEditor() != null ? toFhirContacts(codeSystem.getPermissions().getEditor()) : null);
      fhirCodeSystem.setReviewer(codeSystem.getPermissions().getViewer() != null ? toFhirContacts(codeSystem.getPermissions().getViewer()) : null);
      fhirCodeSystem.setEndorser(codeSystem.getPermissions().getEndorser() != null ? toFhirContacts(codeSystem.getPermissions().getEndorser()) : null);
    }

    fhirCodeSystem.setVersion(version.getVersion());
    fhirCodeSystem.setLanguage(version.getPreferredLanguage());
    fhirCodeSystem.setVersionAlgorithmString(version.getAlgorithm());
    fhirCodeSystem.setDate(OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC));
    fhirCodeSystem.setStatus(version.getStatus());
    fhirCodeSystem.setProperty(toFhirCodeSystemProperty(codeSystem.getProperties(), version.getPreferredLanguage()));

    List<Pair<CodeSystemAssociation, CodeSystemEntityVersion>> entitiesWithAssociations =
        version.getEntities() == null ? List.of() :
            version.getEntities().stream().filter(e -> e.getAssociations() != null)
                .flatMap(e -> e.getAssociations().stream().map(a -> Pair.of(a, e)))
                .filter(p -> codeSystem.getHierarchyMeaning() == null || codeSystem.getHierarchyMeaning().equals(p.getKey().getAssociationType())).toList();
    Map<Long, List<CodeSystemEntityVersion>> childMap =
        entitiesWithAssociations.stream().collect(Collectors.groupingBy(e -> e.getKey().getTargetId(), mapping(Pair::getValue, toList())));
    Map<Long, List<String>> parentMap =
        entitiesWithAssociations.stream().collect(Collectors.groupingBy(e -> e.getKey().getSourceId(), mapping(p -> p.getKey().getTargetCode(), toList())));

    fhirCodeSystem.setConcept(version.getEntities() == null ? null : version.getEntities().stream()
        .filter(e -> codeSystem.getHierarchyMeaning() == null || CollectionUtils.isEmpty(e.getAssociations()))
        .map(e -> toFhir(e, codeSystem, version, childMap, parentMap))
        .sorted(Comparator.comparing(CodeSystemConcept::getCode))
        .collect(Collectors.toList()));
    return fhirCodeSystem;
  }

  private static CodeSystemConcept toFhir(CodeSystemEntityVersion e, CodeSystem codeSystem, CodeSystemVersion version,
                                          Map<Long, List<CodeSystemEntityVersion>> childMap, Map<Long, List<String>> parentMap) {
    CodeSystemConcept concept = new CodeSystemConcept();
    concept.setCode(e.getCode());
    setDesignations(concept, e.getDesignations(), version.getPreferredLanguage());
    concept.setProperty(toFhirConceptProperties(e, codeSystem.getProperties(), childMap, parentMap));
    if (codeSystem.getHierarchyMeaning() != null) {
      concept.setConcept(toFhirConcepts(childMap, parentMap, e.getId(), codeSystem, version));
    }
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

  private static List<CodeSystemConceptProperty> toFhirConceptProperties(CodeSystemEntityVersion entityVersion, List<EntityProperty> properties,
                                                                         Map<Long, List<CodeSystemEntityVersion>> childMap,
                                                                         Map<Long, List<String>> parentMap) {
    List<CodeSystemConceptProperty> conceptProperties = new ArrayList<>();
    if (properties == null) {
      return conceptProperties;
    }

    if (properties.stream().anyMatch(p -> List.of("is-a", "parent", "partOf", "groupedBy", "classifiedWith").contains(p.getName()))) {
      String code =
          properties.stream().filter(p -> List.of("is-a", "parent", "partOf", "groupedBy", "classifiedWith").contains(p.getName())).findFirst().get().getName();
      conceptProperties.addAll(parentMap.getOrDefault(entityVersion.getId(), List.of()).stream()
          .map(c -> new CodeSystemConceptProperty().setCode(code).setValueCode(c)).toList());
    }
    if (properties.stream().anyMatch(p -> "child".equals(p.getName()))) {
      conceptProperties.addAll(childMap.getOrDefault(entityVersion.getId(), List.of()).stream()
          .map(ev -> new CodeSystemConceptProperty().setCode("child").setValueCode(ev.getCode())).toList());
    }
    if (properties.stream().anyMatch(p -> "status".equals(p.getName()))) {
      conceptProperties.add(new CodeSystemConceptProperty().setCode("status").setValueCode(entityVersion.getStatus()));
    }
    if (CollectionUtils.isNotEmpty(entityVersion.getPropertyValues())) {
      Map<Long, EntityProperty> entityProperties = properties.stream().collect(Collectors.toMap(PropertyReference::getId, ep -> ep));
      conceptProperties.addAll(entityVersion.getPropertyValues().stream().map(pv -> {
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
      }).sorted(Comparator.comparing(CodeSystemConceptProperty::getCode)).toList());
    }
    return conceptProperties;
  }

  private static List<CodeSystemConcept> toFhirConcepts(Map<Long, List<CodeSystemEntityVersion>> childMap, Map<Long, List<String>> parentMap,
                                                        Long targetId, CodeSystem codeSystem, CodeSystemVersion version) {
    List<CodeSystemConcept> result = childMap.getOrDefault(targetId, List.of()).stream()
        .map(e -> toFhir(e, codeSystem, version, childMap, parentMap)).collect(Collectors.toList());
    return CollectionUtils.isEmpty(result) ? null : result.stream().sorted(Comparator.comparing(CodeSystemConcept::getCode)).toList();
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
    codeSystem.setPermissions(new Permissions().setAdmin(fromFhirContactsName(fhirCodeSystem.getAuthor()))
        .setEditor(fromFhirContactsName(fhirCodeSystem.getEditor()))
        .setViewer(fromFhirContactsName(fhirCodeSystem.getReviewer()))
        .setEndorser(fromFhirContactsName(fhirCodeSystem.getEndorser())));
    codeSystem.setHierarchyMeaning(fhirCodeSystem.getHierarchyMeaning());
    codeSystem.setContent(fhirCodeSystem.getContent());
    codeSystem.setCaseSensitive(fhirCodeSystem.getCaseSensitive() != null && fhirCodeSystem.getCaseSensitive() ? CaseSignificance.entire_term_case_sensitive :
        CaseSignificance.entire_term_case_insensitive);

    codeSystem.setVersions(fromFhirVersion(fhirCodeSystem));
    codeSystem.setConcepts(fromFhirConcepts(fhirCodeSystem.getConcept(), fhirCodeSystem, null, getParentMap(fhirCodeSystem.getConcept())));
    codeSystem.setProperties(fromFhirProperties(fhirCodeSystem));
    return codeSystem;
  }

  private static Map<String, List<String>> getParentMap(List<CodeSystemConcept> fhirConcepts) {
    if (io.micronaut.core.util.CollectionUtils.isEmpty(fhirConcepts)) {
      return Map.of();
    }
    return fhirConcepts.stream().filter(c -> c.getProperty() != null && c.getProperty().stream()
            .anyMatch(p -> List.of("is-a", "parent", "partOf", "groupedBy", "classifiedWith", "child").contains(p.getCode())))
        .flatMap(c -> c.getProperty().stream().filter(p -> List.of("is-a", "parent", "partOf", "groupedBy", "classifiedWith", "child").contains(p.getCode()))
            .map(p -> {
              if (p.getCode().equals("child")) {
                return Pair.of(p.getValueCode(), c.getCode());
              }
              return Pair.of(c.getCode(), p.getValueCode());
            })).collect(Collectors.groupingBy(Pair::getKey, mapping(Pair::getValue, toList())));
  }

  private static List<CodeSystemVersion> fromFhirVersion(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    CodeSystemVersion version = new CodeSystemVersion();
    version.setCodeSystem(fhirCodeSystem.getId());
    version.setVersion(fhirCodeSystem.getVersion() == null ? "1.0.0" : fhirCodeSystem.getVersion());
    version.setPreferredLanguage(fhirCodeSystem.getLanguage() == null ? Language.en : fhirCodeSystem.getLanguage());
    version.setSupportedLanguages(Optional.ofNullable(fhirCodeSystem.getConcept()).orElse(new ArrayList<>()).stream()
        .filter(c -> c.getDesignation() != null)
        .flatMap(c -> c.getDesignation().stream().map(CodeSystemConceptDesignation::getLanguage)).filter(Objects::nonNull).distinct()
        .collect(Collectors.toList()));
    if (!version.getSupportedLanguages().contains(version.getPreferredLanguage())) {
      version.getSupportedLanguages().add(version.getPreferredLanguage());
    }
    version.setStatus(PublicationStatus.draft);
    version.setAlgorithm(fhirCodeSystem.getVersionAlgorithmString());
    version.setReleaseDate(fhirCodeSystem.getDate() == null ? LocalDate.now() : LocalDate.from(fhirCodeSystem.getDate()));
    return List.of(version);
  }

  private static List<EntityProperty> fromFhirProperties(com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem) {
    List<EntityProperty> properties = getDefEntityProperties();

    if (fhirCodeSystem.getProperty() != null) {
      properties.addAll(fhirCodeSystem.getProperty().stream()
          .filter(p -> properties.stream().noneMatch(ep -> ep.getName().equals(p.getCode())))
          .map(p -> fromFhirProperty(p, fhirCodeSystem.getLanguage())).toList());
    }

    if (fhirCodeSystem.getConcept() != null) {
      properties.addAll(fhirCodeSystem.getConcept().stream()
          .filter(c -> c.getDesignation() != null)
          .flatMap(c -> c.getDesignation().stream())
          .filter(d -> d.getUse() != null && d.getUse().getCode() != null && properties.stream().noneMatch(ep -> ep.getName().equals(d.getUse().getCode())))
          .map(CodeSystemFhirMapper::fromFhirProperty).toList());
    }
    return properties.stream().collect(Collectors.toMap(EntityProperty::getName, p -> p, (p, q) -> p)).values().stream().toList();
  }

  private static List<EntityProperty> getDefEntityProperties() {
    List<EntityProperty> properties = new ArrayList<>();

    EntityProperty display = new EntityProperty();
    display.setName(DISPLAY);
    display.setType(EntityPropertyType.string);
    display.setKind(EntityPropertyKind.designation);
    display.setStatus(PublicationStatus.active);
    properties.add(display);

    EntityProperty definition = new EntityProperty();
    definition.setName(DEFINITION);
    definition.setType(EntityPropertyType.string);
    definition.setKind(EntityPropertyKind.designation);
    definition.setStatus(PublicationStatus.active);
    properties.add(definition);
    return properties;
  }

  private static EntityProperty fromFhirProperty(CodeSystemConceptDesignation d) {
    EntityProperty ep = new EntityProperty();
    ep.setName(d.getUse().getCode());
    ep.setType(EntityPropertyType.string);
    ep.setKind(EntityPropertyKind.designation);
    ep.setStatus(PublicationStatus.active);
    return ep;
  }

  private static EntityProperty fromFhirProperty(CodeSystemProperty p, String lang) {
    EntityProperty property = new EntityProperty();
    property.setName(p.getCode());
    property.setUri(p.getUri());
    property.setDescription(fromFhirName(p.getDescription(), lang));
    property.setType(p.getType());
    property.setKind(EntityPropertyKind.property);
    property.setStatus(PublicationStatus.active);
    return property;
  }

  private static List<Concept> fromFhirConcepts(List<CodeSystemConcept> fhirConcepts,
                                                com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem,
                                                CodeSystemConcept parent, Map<String, List<String>> parentMap) {
    List<Concept> concepts = new ArrayList<>();
    if (io.micronaut.core.util.CollectionUtils.isEmpty(fhirConcepts)) {
      return concepts;
    }
    fhirConcepts.forEach(c -> {
      Concept concept = new Concept();
      concept.setCode(c.getCode());
      concept.setCodeSystem(fhirCodeSystem.getId());
      concept.setVersions(fromFhirConcepts(c, fhirCodeSystem, parent, parentMap));
      concepts.add(concept);
      if (c.getConcept() != null) {
        concepts.addAll(fromFhirConcepts(c.getConcept(), fhirCodeSystem, c, parentMap));
      }
    });
    return concepts;
  }

  private static List<CodeSystemEntityVersion> fromFhirConcepts(CodeSystemConcept c,
                                                                com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem,
                                                                CodeSystemConcept parent, Map<String, List<String>> parentMap) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(c.getCode());
    version.setCodeSystem(codeSystem.getId());
    version.setDesignations(fromFhirDesignations(c, codeSystem));
    version.setPropertyValues(fromFhirProperties(c.getProperty()));
    version.setAssociations(fromFhirAssociations(parent, parentMap.get(c.getCode()), codeSystem));
    version.setStatus(fromFhirStatus(c.getProperty()));
    return List.of(version);
  }

  private static String fromFhirStatus(List<CodeSystemConceptProperty> propertyValues) {
    if (propertyValues == null) {
      return null;
    }
    return propertyValues.stream().filter(pv -> "status".equals(pv.getCode()) && pv.getValueCode() != null).findFirst()
        .map(pv -> PublicationStatus.getStatus(pv.getValueCode()))
        .orElse(PublicationStatus.draft);
  }

  private static List<Designation> fromFhirDesignations(CodeSystemConcept c,
                                                        com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem) {
    String caseSignificance = codeSystem.getCaseSensitive() != null && codeSystem.getCaseSensitive() ? CaseSignificance.entire_term_case_sensitive :
        CaseSignificance.entire_term_case_insensitive;

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
    return propertyValues.stream().filter(v -> !List.of("status", "is-a", "parent", "partOf", "groupedBy", "classifiedWith", "child").contains(v.getCode()))
        .map(v -> {
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

  private static List<CodeSystemAssociation> fromFhirAssociations(CodeSystemConcept parent, List<String> parents,
                                                                  com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem) {
    List<CodeSystemAssociation> associations = new ArrayList<>();
    if (parent != null) {
      CodeSystemAssociation association = new CodeSystemAssociation();
      association.setAssociationType(codeSystem.getHierarchyMeaning() == null ? "is-a" : codeSystem.getHierarchyMeaning());
      association.setStatus(PublicationStatus.active);
      association.setTargetCode(parent.getCode());
      association.setCodeSystem(codeSystem.getId());
      associations.add(association);
    }
    if (CollectionUtils.isNotEmpty(parents)) {
      associations.addAll(parents.stream().distinct().filter(p -> parent == null || !p.equals(parent.getCode())).map(p -> {
        CodeSystemAssociation association = new CodeSystemAssociation();
        association.setAssociationType(codeSystem.getHierarchyMeaning() == null ? "is-a" : codeSystem.getHierarchyMeaning());
        association.setStatus(PublicationStatus.active);
        association.setTargetCode(p);
        association.setCodeSystem(codeSystem.getId());
        return association;
      }).toList());
    }
    return associations;
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
        case "name" -> params.setName(v);
        case "title" -> params.setTitle(v);
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
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW));
    return params;
  }
}
