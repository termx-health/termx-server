package com.kodality.termx.terminology.fileimporter.codesystem.utils;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystem;
import com.kodality.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystemVersion;
import com.kodality.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportResult.FileProcessingEntityPropertyValue;
import com.kodality.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportResult.FileProcessingResponseProperty;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.ContactDetail.Telecom;
import com.kodality.termx.ts.Permissions;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationKind;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemContent;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyRule;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class CodeSystemFileImportMapper {
  public static final String CONCEPT_CODE = "concept-code";
  public static final String HIERARCHICAL_CONCEPT = "hierarchical-concept";
  public static final String CONCEPT_STATUS = "status";
  public static final String CONCEPT_DESCRIPTION = "description";
  public static final String CONCEPT_DISPLAY = "display";
  public static final String CONCEPT_DEFINITION = "definition";
  public static final String CONCEPT_IS_A = "is-a";
  public static final String CONCEPT_PARENT = "parent";
  public static final String CONCEPT_CHILD = "child";
  public static final String CONCEPT_PART_OF = "partOf";
  public static final String CONCEPT_GROUPED_BY = "groupedBy";
  public static final String CONCEPT_CLASSIFIED_WITH = "classifiedWith";
  public static final String OID_SYSTEM = "urn:ietf:rfc:3986";
  public static final String OID_PREFIX = "urn:oid:";

  private static final Map<String, String> associationMap = Map.of(
      "is-a", "is-a",
      "hierarchical-concept", "is-a",
      "parent", "is-a",
      "child", "is-a",
      "partOf", "part-of",
      "groupedBy", "grouped-by",
      "classifiedWith", "classified-with"
  );

  public static CodeSystem toCodeSystem(FileProcessingCodeSystem fpCodeSystem, FileProcessingCodeSystemVersion fpVersion, CodeSystemFileImportResult result,
                                        CodeSystem existingCodeSystem, CodeSystemVersion existingCodeSystemVersion) {
    CodeSystem codeSystem = existingCodeSystem != null ? JsonUtil.fromJson(JsonUtil.toJson(existingCodeSystem), CodeSystem.class) : new CodeSystem();
    codeSystem.setId(fpCodeSystem.getId());
    codeSystem.setUri(fpCodeSystem.getUri() != null ? fpCodeSystem.getUri() : codeSystem.getUri());
    codeSystem.setPublisher(fpCodeSystem.getPublisher() != null ? fpCodeSystem.getPublisher() : codeSystem.getPublisher());
    codeSystem.setName(fpCodeSystem.getName() != null ? fpCodeSystem.getName() : codeSystem.getName());
    codeSystem.setIdentifiers(fpCodeSystem.getOid() != null ? List.of(new Identifier(OID_SYSTEM, OID_PREFIX + fpCodeSystem.getOid())) : codeSystem.getIdentifiers());
    codeSystem.setTitle(fpCodeSystem.getTitle() != null ? fpCodeSystem.getTitle() : codeSystem.getTitle());
    codeSystem.setDescription(fpCodeSystem.getDescription() != null ? fpCodeSystem.getDescription() : codeSystem.getDescription());
    codeSystem.setVersions(List.of(toCsVersion(fpVersion, result.getEntities(), fpCodeSystem, existingCodeSystemVersion)));
    codeSystem.setProperties(toCsProperties(result.getProperties()));
    codeSystem.setConcepts(result.getEntities().stream().map(e -> toCsConcept(codeSystem.getId(), e, result.getEntities())).toList());
    codeSystem.setContent(fpCodeSystem.getSupplement() != null || fpCodeSystem.getSupplementUri() != null ? CodeSystemContent.supplement : CodeSystemContent.complete);
    codeSystem.setBaseCodeSystem(fpCodeSystem.getSupplement());
    codeSystem.setBaseCodeSystemUri(fpCodeSystem.getSupplementUri());
    codeSystem.setExternalWebSource(fpCodeSystem.isExternalWebSource());
    codeSystem.setHierarchyMeaning(toHierarchyMeaning(result.getProperties()).orElse(null));
    codeSystem.setContacts(CollectionUtils.isNotEmpty(fpCodeSystem.getContact()) ? toContacts(fpCodeSystem.getContact()) : codeSystem.getContacts());
    if (fpCodeSystem.getAdmin() != null || fpCodeSystem.getEndorser() != null) {
      Permissions permissions = new Permissions();
      permissions.setAdmin(fpCodeSystem.getAdmin());
      permissions.setEndorser(fpCodeSystem.getEndorser());
      codeSystem.setPermissions(permissions);
    }
    return codeSystem;
  }

  private static List<ContactDetail> toContacts(Map<String, String> contacts) {
    return List.of(new ContactDetail().setTelecoms(contacts.entrySet().stream().map(c -> new Telecom()
            .setValue(c.getValue())
            .setSystem(c.getKey()))
        .toList()));
  }

  private static CodeSystemVersion toCsVersion(FileProcessingCodeSystemVersion fpVersion, List<Map<String, List<FileProcessingEntityPropertyValue>>> entities,
                                               FileProcessingCodeSystem fpCodeSystem, CodeSystemVersion existingCodeSystemVersion) {
    List<String> langs = entities.stream().flatMap(e -> e.values().stream()
        .flatMap(v -> v.stream().map(FileProcessingEntityPropertyValue::getLang))).filter(Objects::nonNull).distinct().toList();

    CodeSystemVersion version = existingCodeSystemVersion != null ? JsonUtil.fromJson(JsonUtil.toJson(existingCodeSystemVersion), CodeSystemVersion.class) : new CodeSystemVersion();
    version.setCodeSystem(fpCodeSystem.getId());
    version.setVersion(fpVersion.getNumber());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(fpVersion.getReleaseDate() == null ? LocalDate.now() : fpVersion.getReleaseDate());
    version.setAlgorithm(fpVersion.getAlgorithm());
    version.setSupportedLanguages(langs);
    version.setBaseCodeSystem(fpCodeSystem.getSupplement());
    version.setBaseCodeSystemUri(fpCodeSystem.getSupplementUri());
    version.setBaseCodeSystemVersion(fpVersion.getSupplementVersion() != null ? new CodeSystemVersionReference().setVersion(fpVersion.getSupplementVersion()) : null);
    version.setPreferredLanguage(fpVersion.getLanguage() != null ? fpVersion.getLanguage() : langs.size() == 1 ? langs.get(0) :
        langs.contains(SessionStore.require().getLang()) ? SessionStore.require().getLang() : null);
    version.setIdentifiers(fpVersion.getOid() != null ? List.of(new Identifier(OID_SYSTEM, OID_PREFIX + fpVersion.getOid())) : version.getIdentifiers());
    return version;
  }

  private static List<EntityProperty> toCsProperties(List<FileProcessingResponseProperty> fpProperties) {
    return fpProperties.stream()
        .filter(fpProperty -> fpProperty.getPropertyType() != null &&
            !List.of(CONCEPT_CODE, CONCEPT_IS_A, HIERARCHICAL_CONCEPT).contains(fpProperty.getPropertyName()))
        .map(fpProperty -> {
          EntityProperty property = new EntityProperty();
          property.setName(fpProperty.getPropertyName());
          property.setType(fpProperty.getPropertyType());
          property.setKind(fpProperty.getPropertyKind());
          property.setStatus(PublicationStatus.active);
          if (StringUtils.isNotEmpty(fpProperty.getPropertyCodeSystem())) {
            property.setRule(new EntityPropertyRule().setCodeSystems(List.of(fpProperty.getPropertyCodeSystem())));
          }
          return property;
        }).collect(Collectors.toList());
  }

  private static Concept toCsConcept(String codeSystem, Map<String, List<FileProcessingEntityPropertyValue>> entity,
                                     List<Map<String, List<FileProcessingEntityPropertyValue>>> entities) {
    if (entity.containsKey(null)) {
      throw ApiError.TE723.toApiException();
    }
    Concept concept = new Concept();
    concept.setCodeSystem(codeSystem);
    concept.setCode(Optional.ofNullable(getEntityProp(entity, CONCEPT_CODE)).orElse(getEntityProp(entity, HIERARCHICAL_CONCEPT)));
    concept.setDescription(getEntityProp(entity, CONCEPT_DESCRIPTION));
    concept.setVersions(List.of(toConceptVersion(codeSystem, entity, entities)));
    return concept;
  }

  private static String getEntityProp(Map<String, List<FileProcessingEntityPropertyValue>> entity, String key) {
    return entity.containsKey(key) ? entity.get(key).stream().findFirst().map(pv -> (String) pv.getValue()).orElse(null) : null;
  }


  // Concept version

  private static CodeSystemEntityVersion toConceptVersion(String codeSystem, Map<String, List<FileProcessingEntityPropertyValue>> entity,
                                                          List<Map<String, List<FileProcessingEntityPropertyValue>>> entities) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCodeSystem(codeSystem);
    version.setCode(Optional.ofNullable(getEntityProp(entity, CONCEPT_CODE)).orElse(getEntityProp(entity, HIERARCHICAL_CONCEPT)));
    version.setDescription(getEntityProp(entity, CONCEPT_DESCRIPTION));
    version.setDesignations(toConceptVersionDesignations(entity));
    version.setPropertyValues(toConceptVersionPropertyValues(entity));
    version.setAssociations(toConceptVersionAssociations(version.getCode(), entity, entities));
    version.setStatus(toConceptVersionStatus(entity));
    return version;
  }

  private static String toConceptVersionStatus(Map<String, List<FileProcessingEntityPropertyValue>> entity) {
    return entity.getOrDefault(CONCEPT_STATUS, List.of()).stream().findFirst().map(s -> PublicationStatus.getStatus((String) s.getValue())).orElse(PublicationStatus.draft);
  }

  private static List<Designation> toConceptVersionDesignations(Map<String, List<FileProcessingEntityPropertyValue>> entity) {
    return entity.keySet().stream()
        .filter(key -> List.of(CONCEPT_DISPLAY, CONCEPT_DEFINITION).contains(key) || entity.get(key).stream().anyMatch(e -> e.getLang() != null))
        .flatMap(key -> {
          List<FileProcessingEntityPropertyValue> fpPropertyValues = entity.get(key);
          return fpPropertyValues.stream().map(fpPropertyValue -> {
            Designation designation = new Designation();
            designation.setName((String) fpPropertyValue.getValue());
            designation.setLanguage(fpPropertyValue.getLang());
            designation.setStatus(PublicationStatus.active);
            designation.setPreferred(CONCEPT_DISPLAY.equals(key));
            designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
            designation.setDesignationType(fpPropertyValue.getPropertyName());
            return designation;
          });
        }).collect(Collectors.toList());
  }

  private static List<EntityPropertyValue> toConceptVersionPropertyValues(Map<String, List<FileProcessingEntityPropertyValue>> entity) {
    return entity.keySet().stream()
        .filter(key -> !List.of(CONCEPT_CODE, CONCEPT_DESCRIPTION, CONCEPT_DISPLAY, CONCEPT_DEFINITION,
            HIERARCHICAL_CONCEPT, CONCEPT_IS_A, CONCEPT_PARENT, CONCEPT_CHILD, CONCEPT_PART_OF, CONCEPT_GROUPED_BY, CONCEPT_CLASSIFIED_WITH,
            CONCEPT_STATUS).contains(key))
        .filter(key -> entity.get(key).stream().noneMatch(e -> e.getLang() != null)).flatMap(key -> {
          List<FileProcessingEntityPropertyValue> fpPropertyValues = entity.get(key);
          return fpPropertyValues.stream().map(fpPropertyValue -> {
            EntityPropertyValue propertyValue = new EntityPropertyValue();
            propertyValue.setValue(fpPropertyValue.getValue());
            propertyValue.setEntityProperty(fpPropertyValue.getPropertyName());
            return propertyValue;
          });
        }).collect(Collectors.toList());
  }

  private static List<CodeSystemAssociation> toConceptVersionAssociations(String code, Map<String, List<FileProcessingEntityPropertyValue>> entity,
                                                                          List<Map<String, List<FileProcessingEntityPropertyValue>>> entities) {
    if (entity.keySet().stream().anyMatch(HIERARCHICAL_CONCEPT::equals)) {
      String parentCode = findParent(code, entities, 1);
      if (parentCode == null) {
        return new ArrayList<>();
      }
      return new ArrayList<>(List.of(new CodeSystemAssociation().setAssociationType(associationMap.get(HIERARCHICAL_CONCEPT)).setTargetCode(parentCode).setStatus(PublicationStatus.active)));
    }

    List<String> parents = findParents(code, entities);
    if (CollectionUtils.isNotEmpty(parents)) {
      return parents.stream().map(p -> {
        CodeSystemAssociation association = new CodeSystemAssociation();
        association.setAssociationType(associationMap.get(CONCEPT_CHILD));
        association.setTargetCode(p);
        association.setStatus(PublicationStatus.active);
        return association;
      }).collect(Collectors.toList());
    }

    return entity.keySet().stream().filter(key -> List.of(CONCEPT_IS_A, CONCEPT_PARENT, CONCEPT_PART_OF, CONCEPT_GROUPED_BY, CONCEPT_CLASSIFIED_WITH).contains(key)).flatMap(key -> {
      List<FileProcessingEntityPropertyValue> fpPropertyValues = entity.get(key);
      return fpPropertyValues.stream().map(fpPropertyValue -> {
        CodeSystemAssociation association = new CodeSystemAssociation();
        association.setAssociationType(associationMap.get(key));
        association.setTargetCode((String) fpPropertyValue.getValue());
        association.setStatus(PublicationStatus.active);
        return association;
      });
    }).collect(Collectors.toList());
  }

  private static List<String> findParents(String child, List<Map<String, List<FileProcessingEntityPropertyValue>>> entities) {
   return entities.stream().map(p -> Pair.of(getEntityProp(p, CONCEPT_CODE), getEntityProp(p, CONCEPT_CHILD)))
       .filter(p -> child != null && child.equals(p.getValue())).map(Pair::getKey).toList();
  }

  private static String findParent(String child, List<Map<String, List<FileProcessingEntityPropertyValue>>> entities, int offset) {
    if (child == null) {
      return null;
    }
    Optional<String> parent = entities.stream().map(p -> getEntityProp(p, HIERARCHICAL_CONCEPT))
        .filter(p -> p != null && child.startsWith(p) && p.length() == child.length() - offset).findFirst();
    if (parent.isPresent()) {
      return parent.get();
    }
    if (child.length() - offset < 1) {
      return null;
    }
    return findParent(child, entities, offset + 1);
  }

  public static List<AssociationType> toAssociationTypes(List<FileProcessingResponseProperty> properties) {
    return toHierarchyMeaning(properties).map(m -> List.of(new AssociationType(m, AssociationKind.codesystemHierarchyMeaning, true)))
        .orElse(List.of());
  }

  private static Optional<String> toHierarchyMeaning(List<FileProcessingResponseProperty> properties) {
    return properties.stream()
        .filter(p -> List.of(HIERARCHICAL_CONCEPT, CONCEPT_IS_A, CONCEPT_PARENT, CONCEPT_CHILD, CONCEPT_PART_OF, CONCEPT_CLASSIFIED_WITH, CONCEPT_GROUPED_BY).contains(p.getPropertyName()))
        .findFirst().map(p -> associationMap.get(p.getPropertyName()));
  }
}
