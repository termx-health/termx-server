package com.kodality.termx.terminology.fhir.codesystem;

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
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.mapset.MapSetService;
import com.kodality.termx.terminology.terminology.relatedartifacts.CodeSystemRelatedArtifactService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetService;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.Copyright;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.Permissions;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemContent;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyKind;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.property.PropertyReference;
import com.kodality.termx.ts.relatedartifact.RelatedArtifactType;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Period;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Slf4j
@Context
public class CodeSystemFhirMapper extends BaseFhirMapper {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final ValueSetService valueSetService;
  private final MapSetService mapSetService;
  private final CodeSystemRelatedArtifactService relatedArtifactService;
  private static final String DISPLAY = "display";
  private static final String DEFINITION = "definition";
  private static Optional<String> termxWebUrl;


  public CodeSystemFhirMapper(ConceptService conceptService,
                              CodeSystemService codeSystemService, ValueSetService valueSetService, MapSetService mapSetService,
                              CodeSystemRelatedArtifactService relatedArtifactService, @Value("${termx.web-url}") Optional<String> termxWebUrl) {
    this.conceptService = conceptService;
    this.codeSystemService = codeSystemService;
    this.valueSetService = valueSetService;
    this.mapSetService = mapSetService;
    this.relatedArtifactService = relatedArtifactService;
    CodeSystemFhirMapper.termxWebUrl = termxWebUrl;
  }

  // -------------- TO FHIR --------------

  public static String toFhirId(CodeSystem cs, CodeSystemVersion csv) {
    return cs.getId() + BaseFhirMapper.SEPARATOR + csv.getVersion();
  }

  public String toFhirJson(CodeSystem cs, CodeSystemVersion csv, List<Provenance> provenances) {
    return FhirMapper.toJson(toFhir(cs, csv, provenances));
  }

  public com.kodality.zmei.fhir.resource.terminology.CodeSystem toFhir(CodeSystem codeSystem, CodeSystemVersion version, List<Provenance> provenances) {
    com.kodality.zmei.fhir.resource.terminology.CodeSystem fhirCodeSystem = new com.kodality.zmei.fhir.resource.terminology.CodeSystem();
    termxWebUrl.ifPresent(url -> fhirCodeSystem.addExtension(toFhirWebSourceExtension(url, codeSystem.getId())));
    fhirCodeSystem.setId(toFhirId(codeSystem, version));
    fhirCodeSystem.setUrl(codeSystem.getUri());
    fhirCodeSystem.setPublisher(conceptService.load("publisher", codeSystem.getPublisher()).flatMap(Concept::getLastVersion).flatMap(CodeSystemEntityVersion::getDisplay).orElse(codeSystem.getPublisher()));
    fhirCodeSystem.setName(codeSystem.getName());
    if (CollectionUtils.isNotEmpty(codeSystem.getOtherTitle())) {
      codeSystem.getOtherTitle().forEach(otherName -> fhirCodeSystem.addExtension(
          toFhirOtherTitleExtension("http://hl7.org/fhir/StructureDefinition/codesystem-otherTitle", otherName)));
    }
    fhirCodeSystem.setTitle(toFhirName(codeSystem.getTitle(), version.getPreferredLanguage()));
    fhirCodeSystem.setPrimitiveExtensions("title", toFhirTranslationExtension(codeSystem.getTitle(), version.getPreferredLanguage()));
    LocalizedName description = joinDescriptions(codeSystem.getDescription(), version.getDescription());
    fhirCodeSystem.setDescription(toFhirName(description, version.getPreferredLanguage()));
    fhirCodeSystem.setPrimitiveExtensions("description", toFhirTranslationExtension(description, version.getPreferredLanguage()));
    fhirCodeSystem.setPurpose(toFhirName(codeSystem.getPurpose(), version.getPreferredLanguage()));
    fhirCodeSystem.setTopic(toFhirTopic(codeSystem.getTopic()));
    fhirCodeSystem.setUseContext(toFhirUseContext(codeSystem.getUseContext()));
    fhirCodeSystem.setPrimitiveExtensions("purpose", toFhirTranslationExtension(codeSystem.getPurpose(), version.getPreferredLanguage()));
    fhirCodeSystem.setText(toFhirText(codeSystem.getNarrative()));
    fhirCodeSystem.setExperimental(codeSystem.getExperimental() != null && codeSystem.getExperimental());
    Optional.ofNullable(StringUtils.isEmpty(codeSystem.getSourceReference()) ? null : codeSystem.getSourceReference()).ifPresent(ref -> fhirCodeSystem.addExtension(toFhirSourceReferenceExtension("http://hl7.org/fhir/StructureDefinition/codesystem-sourceReference", ref)));
    Optional.ofNullable(codeSystem.getReplaces()).flatMap(id -> codeSystemService.load(id).map(CodeSystem::getUri)).ifPresent(uri -> fhirCodeSystem.addExtension(toFhirReplacesExtension(uri)));
    fhirCodeSystem.setIdentifier(toFhirIdentifiers(codeSystem.getIdentifiers(), version.getIdentifiers()));
    fhirCodeSystem.setContact(toFhirContacts(codeSystem.getContacts()));
    fhirCodeSystem.setDate(toFhirOffsetDateTime(provenances));
    fhirCodeSystem.setLastReviewDate(toFhirDate(provenances, "reviewed"));
    fhirCodeSystem.setApprovalDate(toFhirDate(provenances, "approved"));
    fhirCodeSystem.setContent(codeSystem.getContent());
    fhirCodeSystem.setHierarchyMeaning(codeSystem.getHierarchyMeaning());
    fhirCodeSystem.addExtension(new Extension("http://hl7.org/fhir/StructureDefinition/cqf-knowledgeRepresentationLevel").setValueCode("executable"));
    if (!CodeSystemContent.supplement.equals(fhirCodeSystem.getContent())) {
      fhirCodeSystem.setCaseSensitive(
          codeSystem.getCaseSensitive() != null && !CaseSignificance.entire_term_case_insensitive.equals(codeSystem.getCaseSensitive()));
    }
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
    if (CollectionUtils.isNotEmpty(codeSystem.getConfigurationAttributes())) {
      Map<String, Concept> concepts = conceptService.query(new ConceptQueryParams().setCodeSystem("termx-resource-configuration").all()).getData().stream()
          .collect(Collectors.toMap(Concept::getCode, c -> c));
      toFhir(fhirCodeSystem, codeSystem.getConfigurationAttributes(), concepts);
    }

    List<String> relatedArtifacts = relatedArtifactService.findRelatedArtifacts(codeSystem.getId()).stream()
        .map(a -> {
          if (RelatedArtifactType.cs.equals(a.getType())) {
            return codeSystemService.load(a.getId()).map(CodeSystem::getUri).orElse(null);
          } else if (RelatedArtifactType.vs.equals(a.getType())) {
            return Optional.ofNullable(valueSetService.load(a.getId())).map(ValueSet::getUri).orElse(null);
          } else if (RelatedArtifactType.ms.equals(a.getType())) {
            return mapSetService.load(a.getId()).map(MapSet::getUri).orElse(null);
          }
          return null;
        }).filter(Objects::nonNull).toList();
//    toFhirRelatedArtifacts(fhirCodeSystem, relatedArtifacts);

    fhirCodeSystem.setVersion(version.getVersion());
    fhirCodeSystem.setLanguage(version.getPreferredLanguage());
    fhirCodeSystem.setVersionAlgorithmString(version.getAlgorithm());
    fhirCodeSystem.setEffectivePeriod(new Period(
        OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC),
        version.getExpirationDate() == null ? null : OffsetDateTime.of(version.getExpirationDate().atTime(23, 59), ZoneOffset.UTC)));
    fhirCodeSystem.setStatus(version.getStatus());
    fhirCodeSystem.setProperty(toFhirCodeSystemProperty(codeSystem.getProperties(), version.getPreferredLanguage()));

    List<Pair<CodeSystemAssociation, CodeSystemEntityVersion>> entitiesWithAssociations =
        version.getEntities() == null ? List.of() :
            version.getEntities().stream().filter(e -> e.getAssociations() != null)
                .flatMap(e -> e.getAssociations().stream().map(a -> Pair.of(a, e)))
                .filter(p -> codeSystem.getHierarchyMeaning() != null && codeSystem.getHierarchyMeaning().equals(p.getKey().getAssociationType())).toList();
    Map<Long, List<CodeSystemEntityVersion>> childMap =
        entitiesWithAssociations.stream().collect(Collectors.groupingBy(e -> e.getKey().getTargetId(), mapping(Pair::getValue, toList())));
    Map<Long, List<String>> parentMap =
        entitiesWithAssociations.stream().collect(Collectors.groupingBy(e -> e.getKey().getSourceId(), mapping(p -> p.getKey().getTargetCode(), toList())));

    fhirCodeSystem.setConcept(version.getEntities() == null ? null : version.getEntities().stream()
        .filter(e -> codeSystem.getHierarchyMeaning() == null || e.getAssociations() == null ||
                     e.getAssociations().stream().noneMatch(a -> a.getAssociationType().equals(codeSystem.getHierarchyMeaning())))
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
    concept.setProperty(toFhirConceptProperties(e, codeSystem, childMap, parentMap));
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
    concept.setDesignation(toFhirDesignations(designations.stream().filter(d ->  display != d && definition != d).toList()));
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

  private static List<CodeSystemConceptProperty> toFhirConceptProperties(CodeSystemEntityVersion entityVersion,
                                                                         CodeSystem codeSystem,
                                                                         Map<Long, List<CodeSystemEntityVersion>> childMap,
                                                                         Map<Long, List<String>> parentMap) {
    List<EntityProperty> properties = codeSystem.getProperties();
    List<CodeSystemConceptProperty> conceptProperties = new ArrayList<>();
    if (properties == null) {
      return conceptProperties;
    }

    if (properties.stream().anyMatch(p -> List.of("is-a", "parent", "partOf", "groupedBy", "classifiedWith").contains(p.getName()))) {
      String code = properties.stream().filter(p -> List.of("is-a", "parent", "partOf", "groupedBy", "classifiedWith").contains(p.getName())).findFirst().get().getName();
      conceptProperties.addAll(parentMap.getOrDefault(entityVersion.getId(), List.of()).stream()
          .map(c -> new CodeSystemConceptProperty().setCode(code).setValueCode(c)).toList());
      conceptProperties.addAll(Optional.ofNullable(entityVersion.getAssociations()).orElse(List.of()).stream()
          .filter(a -> !a.getAssociationType().equals(codeSystem.getHierarchyMeaning()))
          .map(a -> new CodeSystemConceptProperty().setCode(code).setValueCode(a.getTargetCode())).toList());
    }
    if (properties.stream().anyMatch(p -> "child".equals(p.getName()))) {
      conceptProperties.addAll(childMap.getOrDefault(entityVersion.getId(), List.of()).stream()
          .map(ev -> new CodeSystemConceptProperty().setCode("child").setValueCode(ev.getCode())).toList());
    }
    if (properties.stream().anyMatch(p -> "status".equals(p.getName()))) {
      conceptProperties.add(new CodeSystemConceptProperty().setCode("status").setValueCode(entityVersion.getStatus()));
    }
    if (properties.stream().anyMatch(p -> "modifiedAt".equals(p.getName()))) {
      conceptProperties.add(new CodeSystemConceptProperty().setCode("modifiedAt").setValueDateTime(entityVersion.getSysModifiedAt()));
    }
    if (properties.stream().anyMatch(p -> "modifiedBy".equals(p.getName()))) {
      conceptProperties.add(new CodeSystemConceptProperty().setCode("modifiedBy").setValueString(entityVersion.getSysModifiedBy()));
    }
    if (CollectionUtils.isNotEmpty(entityVersion.getPropertyValues())) {
      Map<String, List<EntityProperty>> entityProperties = properties.stream().collect(Collectors.groupingBy(PropertyReference::getName));
      conceptProperties.addAll(entityVersion.getPropertyValues().stream().map(pv -> {
        EntityProperty entityProperty = entityProperties.getOrDefault(pv.getEntityProperty(), List.of()).stream().findFirst().orElse(null);
        if (entityProperty == null) {
          return null;
        }
        CodeSystemConceptProperty fhir = new CodeSystemConceptProperty();
        fhir.setCode(entityProperty.getName());
        switch (entityProperty.getType()) {
          case EntityPropertyType.code -> fhir.setValueCode(pv.getValue() instanceof String ? (String) pv.getValue() : String.valueOf(pv.getValue()));
          case EntityPropertyType.string -> fhir.setValueString(pv.getValue() instanceof String ? (String) pv.getValue() : String.valueOf(pv.getValue()));
          case EntityPropertyType.bool -> fhir.setValueBoolean((Boolean) pv.getValue());
          case EntityPropertyType.decimal -> fhir.setValueDecimal(new BigDecimal(String.valueOf(pv.getValue())));
          case EntityPropertyType.integer -> fhir.setValueInteger(Integer.valueOf(String.valueOf(pv.getValue())));
          case EntityPropertyType.coding -> {
            Concept concept = JsonUtil.getObjectMapper().convertValue(pv.getValue(), Concept.class);
            if (concept.getCodeSystem() == null || concept.getCode() == null) {
              return null;
            }
            fhir.setValueCoding(new Coding(concept.getCodeSystem(), concept.getCode()));
          }
          case EntityPropertyType.dateTime -> {
            if (pv.getValue() instanceof OffsetDateTime) {
              fhir.setValueDateTime((OffsetDateTime) pv.getValue());
            } else {
              fhir.setValueDateTime(DateUtil.parseOffsetDateTime(pv.getValue() instanceof String ? (String) pv.getValue() : String.valueOf(pv.getValue())));
            }
          }
        }
        return fhir;
      }).filter(Objects::nonNull).sorted(Comparator.comparing(CodeSystemConceptProperty::getCode)).toList());
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
    version.setReleaseDate(fhirCodeSystem.getEffectivePeriod() == null || fhirCodeSystem.getEffectivePeriod().getStart() == null ? LocalDate.now() :
        LocalDate.from(fhirCodeSystem.getEffectivePeriod().getStart()));
    version.setExpirationDate(fhirCodeSystem.getEffectivePeriod() == null || fhirCodeSystem.getEffectivePeriod().getEnd() == null ? null :
        LocalDate.from(fhirCodeSystem.getEffectivePeriod().getEnd()));
    version.setIdentifiers(fromFhirVersionIdentifiers(fhirCodeSystem.getIdentifier()));
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
        case "_id" -> params.setIds(CodeSystemFhirMapper.parseCompositeId(v)[0]);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.26 :token
        case "code" -> params.setConceptCode(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.27 :token
        case "content-mode" -> params.setContent(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.33 :date
        // Allowed: eq, ne, gt, ge, lt, le, sa, eb, ap
        case "date" -> {
          if (v.startsWith("ge")) {
            params.setVersionReleaseDateGe(LocalDate.parse(v.substring(2)));
          } else {
            params.setVersionReleaseDate(LocalDate.parse(v));
          }
        }
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.35 :string
        case "description:exact" -> params.setDescription(v);
        case "description" -> params.setDescriptionStarts(v);
        case "description:contains" -> params.setDescriptionContains(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.37 :token
        case "identifier" -> params.setIdentifier(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.40 :string
        case "name:exact" -> params.setName(v);
        case "name" -> params.setNameStarts(v);
        case "name:contains" -> params.setNameContains(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.42 :string
        case "publisher:exact" -> params.setPublisher(v);
        case "publisher" -> params.setPublisherStarts(v);
        case "publisher:contains" -> params.setPublisherContains(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.43 :token
        case "status" -> params.setVersionStatus(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.44 :reference
        case "supplements" -> params.setBaseCodeSystem(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.45 :uri
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.48 :uri
        case "system", "url" -> params.setUri(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.46 :string
        case "title:exact" -> params.setTitle(v);
        case "title" -> params.setTitleStarts(v);
        case "title:contains" -> params.setTitleContains(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.49 :uri
        case "version" -> params.setVersionVersion(v);
        default -> throw new ApiClientException("Search by '" + k + "' not supported");
      }
    });
    params.setVersionsDecorated(true);
    params.setPropertiesDecorated(true);
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW));
    return params;
  }

  public static CodeSystemVersionQueryParams fromFhirCSVersionParams(SearchCriterion fhir) {
    CodeSystemVersionQueryParams params = new CodeSystemVersionQueryParams();
    getSimpleParams(fhir).forEach((k, v) -> {
      switch (k) {
        case SearchCriterion._COUNT -> params.setLimit(fhir.getCount());
        case SearchCriterion._PAGE -> params.setOffset(getOffset(fhir));
        case "_id" -> params.setCodeSystem(CodeSystemFhirMapper.parseCompositeId(v)[0]);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.26 :token
        case "code" -> params.setConceptCode(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.27 :token
        case "content-mode" -> params.setCodeSystemContent(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.33 :date
        // Allowed: eq, ne, gt, ge, lt, le, sa, eb, ap
        case "date" -> {
          if (v.startsWith("ge")) {
            params.setReleaseDateGe(LocalDate.parse(v.substring(2)));
          } else {
            params.setReleaseDate(LocalDate.parse(v));
          }
        }
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.35 :string
        case "description:exact" -> params.setCodeSystemDescription(v);
        case "description" -> params.setCodeSystemDescriptionStarts(v);
        case "description:contains" -> params.setCodeSystemDescriptionContains(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.37 :token
        case "identifier" -> params.setIdentifier(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.40 :string
        case "name:exact" -> params.setCodeSystemName(v);
        case "name" -> params.setCodeSystemNameStarts(v);
        case "name:contains" -> params.setCodeSystemNameContains(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.42 :string
        case "publisher:exact" -> params.setCodeSystemPublisher(v);
        case "publisher" -> params.setCodeSystemPublisherStarts(v);
        case "publisher:contains" -> params.setCodeSystemPublisherContains(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.43 :token
        case "status" -> params.setStatus(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.45 :uri
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.48 :uri
        case "system", "url" -> params.setCodeSystemUri(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.46 :uri
        case "title:exact" -> params.setCodeSystemTitle(v);
        case "title" -> params.setCodeSystemTitleStarts(v);
        case "title:contains" -> params.setCodeSystemTitleContains(v);
        // https://www.hl7.org/fhir/codesystem-search.html#4.8.49 :uri
        case "version" -> params.setVersion(v);
        default -> throw new ApiClientException("Search by '" + k + "' not supported");
      }
    });
    params.setPermittedCodeSystems(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW));
    return params;
  }
}
