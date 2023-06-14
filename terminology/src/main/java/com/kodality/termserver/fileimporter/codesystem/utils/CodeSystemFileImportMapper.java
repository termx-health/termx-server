package com.kodality.termserver.fileimporter.codesystem.utils;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystem;
import com.kodality.termserver.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystemVersion;
import com.kodality.termserver.fileimporter.codesystem.utils.CodeSystemFileImportResult.FileProcessingEntityPropertyValue;
import com.kodality.termserver.fileimporter.codesystem.utils.CodeSystemFileImportResult.FileProcessingResponseProperty;
import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.association.AssociationKind;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemContent;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CodeSystemFileImportMapper {
  public static final String CONCEPT_CODE = "concept-code";
  public static final String CONCEPT_DESCRIPTION = "description";
  public static final String CONCEPT_DISPLAY = "display";
  public static final String CONCEPT_DEFINITION = "definition";
  public static final String CONCEPT_PARENT = "is-a";

  public static CodeSystem toCodeSystem(FileProcessingCodeSystem fpCodeSystem, FileProcessingCodeSystemVersion fpVersion, CodeSystemFileImportResult result,
                                        CodeSystem existingCodeSystem, CodeSystemVersion existingCodeSystemVersion) {
    CodeSystem codeSystem = existingCodeSystem != null ? JsonUtil.fromJson(JsonUtil.toJson(existingCodeSystem), CodeSystem.class) : new CodeSystem();
    codeSystem.setId(fpCodeSystem.getId());
    codeSystem.setUri(fpCodeSystem.getUri() != null ? fpCodeSystem.getUri() : codeSystem.getUri());
    codeSystem.setNames(fpCodeSystem.getNames() != null ? fpCodeSystem.getNames() : codeSystem.getNames());
    codeSystem.setDescription(fpCodeSystem.getDescription() != null ? fpCodeSystem.getDescription() : codeSystem.getDescription());
    codeSystem.setVersions(List.of(existingCodeSystemVersion != null ? existingCodeSystemVersion : toCsVersion(fpVersion, fpCodeSystem.getId())));
    codeSystem.setProperties(toCsProperties(result.getProperties()));
    codeSystem.setConcepts(result.getEntities().stream().map(e -> toCsConcept(codeSystem.getId(), e)).toList());
    codeSystem.setContent(CodeSystemContent.complete);
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
        .filter(fpProperty -> fpProperty.getPropertyType() != null && !List.of(CONCEPT_CODE, CONCEPT_PARENT).contains(fpProperty.getPropertyName()))
        .map(fpProperty -> {
          EntityProperty property = new EntityProperty();
          property.setName(fpProperty.getPropertyName());
          property.setType(fpProperty.getPropertyType());
          property.setStatus(PublicationStatus.active);
          return property;
        }).collect(Collectors.toList());
  }

  private static Concept toCsConcept(String codeSystem, Map<String, List<FileProcessingEntityPropertyValue>> entity) {
    Concept concept = new Concept();
    concept.setCodeSystem(codeSystem);
    concept.setCode(getEntityProp(entity, CONCEPT_CODE));
    concept.setDescription(getEntityProp(entity, CONCEPT_DESCRIPTION));
    concept.setVersions(List.of(toConceptVersion(codeSystem, entity)));
    return concept;
  }

  private static String getEntityProp(Map<String, List<FileProcessingEntityPropertyValue>> entity, String key) {
    return entity.containsKey(key) ? entity.get(key).stream().findFirst().map(pv -> (String) pv.getValue()).orElse(null) : null;
  }


  // Concept version

  private static CodeSystemEntityVersion toConceptVersion(String codeSystem, Map<String, List<FileProcessingEntityPropertyValue>> entity) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCodeSystem(codeSystem);
    version.setCode(getEntityProp(entity, CONCEPT_CODE));
    version.setDescription(getEntityProp(entity, CONCEPT_DESCRIPTION));
    version.setDesignations(toConceptVersionDesignations(entity));
    version.setPropertyValues(toConceptVersionPropertyValues(entity));
    version.setAssociations(toConceptVersionAssociations(entity));
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
        .filter(key -> !List.of(CONCEPT_CODE, CONCEPT_DESCRIPTION, CONCEPT_DISPLAY, CONCEPT_DEFINITION, CONCEPT_PARENT).contains(key))
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

  private static List<CodeSystemAssociation> toConceptVersionAssociations(Map<String, List<FileProcessingEntityPropertyValue>> entity) {
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


  // Value Set
  public static ValueSet toValueSet(CodeSystem codeSystem, ValueSet existingValueSet) {
    ValueSet valueSet = existingValueSet == null ? new ValueSet() : existingValueSet;
    valueSet.setId(codeSystem.getId());
    valueSet.setUri(codeSystem.getUri() == null ? valueSet.getUri() : codeSystem.getUri());
    valueSet.setTitle(codeSystem.getNames() == null ? valueSet.getTitle() : codeSystem.getNames());
    return valueSet;
  }

  public static ValueSetVersion toValueSetVersion(FileProcessingCodeSystemVersion fpVersion, String valueSet, CodeSystemVersion codeSystemVersion) {
    ValueSetVersion version = new ValueSetVersion();
    version.setValueSet(valueSet);
    version.setVersion(fpVersion.getVersion());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(fpVersion.getReleaseDate());
    version.setRuleSet(new ValueSetVersionRuleSet().setRules(List.of(
        new ValueSetVersionRule()
            .setType(ValueSetVersionRuleType.include)
            .setCodeSystem(codeSystemVersion.getCodeSystem())
            .setCodeSystemVersion(codeSystemVersion)
    )));
    return version;
  }

  public static List<AssociationType> toAssociationTypes(List<FileProcessingResponseProperty> properties) {
    return properties.stream().anyMatch(p -> CONCEPT_PARENT.equals(p.getPropertyName())) ?
        List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true)) : List.of();
  }
}
