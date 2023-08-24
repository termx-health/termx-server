package com.kodality.termx.fileimporter.codesystem.utils;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ApiError;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystem;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystemVersion;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportResult.FileProcessingEntityPropertyValue;
import com.kodality.termx.fileimporter.codesystem.utils.CodeSystemFileImportResult.FileProcessingResponseProperty;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationKind;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemContent;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CodeSystemFileImportMapper {
  public static final String CONCEPT_CODE = "concept-code";
  public static final String HIERARCHICAL_CONCEPT = "hierarchical-concept";
  public static final String CONCEPT_DESCRIPTION = "description";
  public static final String CONCEPT_DISPLAY = "display";
  public static final String CONCEPT_DEFINITION = "definition";
  public static final String CONCEPT_PARENT = "is-a";

  public static CodeSystem toCodeSystem(FileProcessingCodeSystem fpCodeSystem, FileProcessingCodeSystemVersion fpVersion, CodeSystemFileImportResult result,
                                        CodeSystem existingCodeSystem, CodeSystemVersion existingCodeSystemVersion) {
    CodeSystem codeSystem = existingCodeSystem != null ? JsonUtil.fromJson(JsonUtil.toJson(existingCodeSystem), CodeSystem.class) : new CodeSystem();
    codeSystem.setId(fpCodeSystem.getId());
    codeSystem.setUri(fpCodeSystem.getUri() != null ? fpCodeSystem.getUri() : codeSystem.getUri());
    codeSystem.setTitle(fpCodeSystem.getTitle() != null ? fpCodeSystem.getTitle() : codeSystem.getTitle());
    codeSystem.setDescription(fpCodeSystem.getDescription() != null ? fpCodeSystem.getDescription() : codeSystem.getDescription());
    codeSystem.setVersions(List.of(existingCodeSystemVersion != null ? existingCodeSystemVersion : toCsVersion(fpVersion, fpCodeSystem.getId())));
    codeSystem.setProperties(toCsProperties(result.getProperties()));
    codeSystem.setConcepts(result.getEntities().stream().map(e -> toCsConcept(codeSystem.getId(), e, result.getEntities())).toList());
    codeSystem.setContent(CodeSystemContent.complete);
    codeSystem.setHierarchyMeaning(result.getProperties().stream().anyMatch(p -> List.of(CONCEPT_PARENT, HIERARCHICAL_CONCEPT).contains(p.getPropertyName())) ? "is-a" : null);
    return codeSystem;
  }

  private static CodeSystemVersion toCsVersion(FileProcessingCodeSystemVersion fpVersion, String codeSystem) {
    CodeSystemVersion version = new CodeSystemVersion();
    version.setCodeSystem(codeSystem);
    version.setVersion(fpVersion.getVersion());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(fpVersion.getReleaseDate());
    return version;
  }

  private static List<EntityProperty> toCsProperties(List<FileProcessingResponseProperty> fpProperties) {
    return fpProperties.stream()
        .filter(fpProperty -> fpProperty.getPropertyType() != null &&
            !List.of(CONCEPT_CODE, CONCEPT_PARENT, HIERARCHICAL_CONCEPT).contains(fpProperty.getPropertyName()))
        .map(fpProperty -> {
          EntityProperty property = new EntityProperty();
          property.setName(fpProperty.getPropertyName());
          property.setType(fpProperty.getPropertyType());
          property.setKind(fpProperty.getPropertyKind());
          property.setStatus(PublicationStatus.active);
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
    version.setStatus(PublicationStatus.draft);
    return version;
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
        .filter(key -> !List.of(CONCEPT_CODE, CONCEPT_DESCRIPTION, CONCEPT_DISPLAY, CONCEPT_DEFINITION, CONCEPT_PARENT, HIERARCHICAL_CONCEPT).contains(key))
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
      return new ArrayList<>(List.of(new CodeSystemAssociation().setAssociationType("is-a").setTargetCode(parentCode).setStatus(PublicationStatus.active)));
    }

    return entity.keySet().stream().filter(CONCEPT_PARENT::equals).flatMap(key -> {
      List<FileProcessingEntityPropertyValue> fpPropertyValues = entity.get(key);
      return fpPropertyValues.stream().map(fpPropertyValue -> {
        CodeSystemAssociation association = new CodeSystemAssociation();
        association.setAssociationType("is-a");
        association.setTargetCode((String) fpPropertyValue.getValue());
        association.setStatus(PublicationStatus.active);
        return association;
      });
    }).collect(Collectors.toList());
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
    return properties.stream().anyMatch(p -> CONCEPT_PARENT.equals(p.getPropertyName()) || HIERARCHICAL_CONCEPT.equals(p.getPropertyName())) ?
        List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true)) : List.of();
  }
}
