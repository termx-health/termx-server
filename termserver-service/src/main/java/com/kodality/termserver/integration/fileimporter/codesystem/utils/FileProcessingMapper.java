package com.kodality.termserver.integration.fileimporter.codesystem.utils;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.CaseSignificance;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemContent;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingCodeSystem;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingRequest.FileProcessingCodeSystemVersion;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingResponse.FileProcessingEntityPropertyValue;
import com.kodality.termserver.integration.fileimporter.codesystem.utils.FileProcessingResponse.FileProcessingResponseProperty;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.valueset.ValueSetVersionRuleType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FileProcessingMapper {
  public static final String CONCEPT_CODE = "concept-code";
  public static final String CONCEPT_DESCRIPTION = "description";
  public static final String CONCEPT_DISPLAY = "display";
  public static final String CONCEPT_DEFINITION = "definition";

  public CodeSystem toCodeSystem(FileProcessingCodeSystem fpCodeSystem, CodeSystem existingCodeSystem) {
    CodeSystem codeSystem = existingCodeSystem == null ? new CodeSystem() : existingCodeSystem;
    codeSystem.setId(fpCodeSystem.getId());
    codeSystem.setUri(fpCodeSystem.getUri() == null ? codeSystem.getUri() : fpCodeSystem.getUri());
    codeSystem.setNames(fpCodeSystem.getNames() == null ? codeSystem.getNames() : fpCodeSystem.getNames());
    codeSystem.setContent(CodeSystemContent.complete);
    codeSystem.setDescription(fpCodeSystem.getDescription() == null ? codeSystem.getDescription() : fpCodeSystem.getDescription());
    return codeSystem;
  }

  public CodeSystemVersion toCodeSystemVersion(FileProcessingCodeSystemVersion fpVersion, String codeSystem) {
    CodeSystemVersion version = new CodeSystemVersion();
    version.setCodeSystem(codeSystem);
    version.setVersion(fpVersion.getVersion());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(fpVersion.getReleaseDate());
    return version;
  }

  public List<EntityProperty> toProperties(List<FileProcessingResponseProperty> fpProperties) {
    return fpProperties.stream().filter(fpProperty -> fpProperty.getPropertyType() != null)
        .map(fpProperty -> {
          EntityProperty property = new EntityProperty();
          property.setName(fpProperty.getPropertyName());
          property.setType(fpProperty.getPropertyType());
          property.setStatus(PublicationStatus.active);
          return property;
        }).collect(Collectors.toList());
  }

  public List<Concept> toConcepts(List<Map<String, List<FileProcessingEntityPropertyValue>>> entities, List<EntityProperty> properties) {
    return entities.stream().map(entity -> {
      Concept concept = new Concept();
      concept.setCode(toConceptCode(entity));
      concept.setDescription(toConceptDescription(entity));
      concept.setVersions(List.of(toConceptVersion(entity, properties)));
      return concept;
    }).collect(Collectors.toList());
  }

  private String toConceptCode(Map<String, List<FileProcessingEntityPropertyValue>> entity) {
    return entity.get(CONCEPT_CODE).stream().findFirst().map(pv -> (String) pv.getValue()).orElseThrow(ApiError.TE702::toApiException);
  }

  private String toConceptDescription(Map<String, List<FileProcessingEntityPropertyValue>> entity) {
    return entity.containsKey(CONCEPT_DESCRIPTION) ? entity.get(CONCEPT_DESCRIPTION).stream().findFirst().map(pv -> (String) pv.getValue()).orElse(null) : null;
  }

  private CodeSystemEntityVersion toConceptVersion(Map<String, List<FileProcessingEntityPropertyValue>> entity, List<EntityProperty> properties) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setStatus(PublicationStatus.draft);
    version.setCode(toConceptCode(entity));
    version.setPropertyValues(toConceptPropertyValues(entity, properties));
    version.setDesignations(toConceptDesignations(entity, properties));
    return version;
  }

  private List<EntityPropertyValue> toConceptPropertyValues(Map<String, List<FileProcessingEntityPropertyValue>> entity, List<EntityProperty> properties) {
    return entity.keySet().stream()
        .filter(key -> !List.of(CONCEPT_CODE, CONCEPT_DESCRIPTION, CONCEPT_DISPLAY, CONCEPT_DEFINITION).contains(key))
        .filter(key -> entity.get(key).stream().noneMatch(e -> e.getLang() != null))
        .flatMap(key -> {
          List<FileProcessingEntityPropertyValue> fpPropertyValues = entity.get(key);
          return fpPropertyValues.stream().map(fpPropertyValue -> {
            EntityPropertyValue propertyValue = new EntityPropertyValue();
            propertyValue.setValue(fpPropertyValue.getValue());
            propertyValue.setEntityPropertyId(properties.stream()
                .filter(p -> p.getName().equals(fpPropertyValue.getPropertyName()))
                .map(EntityProperty::getId)
                .findFirst().orElseThrow(ApiError.TE703::toApiException));
            return propertyValue;
          });
        }).collect(Collectors.toList());
  }

  private List<Designation> toConceptDesignations(Map<String, List<FileProcessingEntityPropertyValue>> entity, List<EntityProperty> properties) {
    return entity.keySet().stream()
        .filter(key -> List.of(CONCEPT_DISPLAY, CONCEPT_DEFINITION).contains(key) || entity.get(key).stream().anyMatch(e -> e.getLang() != null))
        .flatMap(key -> {
          List<FileProcessingEntityPropertyValue> fpPropertyValues = entity.get(key);
          return fpPropertyValues.stream().map(fpPropertyValue -> {
            Designation designation = new Designation();
            designation.setName((String) fpPropertyValue.getValue());
            designation.setLanguage(fpPropertyValue.getLang());
            designation.setStatus(PublicationStatus.active);
            designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
            designation.setDesignationTypeId(properties.stream()
                .filter(p -> p.getName().equals(fpPropertyValue.getPropertyName()))
                .map(EntityProperty::getId)
                .findFirst().orElseThrow(ApiError.TE703::toApiException));
            return designation;
          });
        }).collect(Collectors.toList());
  }

  public ValueSet toValueSet(CodeSystem codeSystem, ValueSet existingValueSet) {
    ValueSet valueSet = existingValueSet == null ? new ValueSet() : existingValueSet;
    valueSet.setId(codeSystem.getId());
    valueSet.setUri(codeSystem.getUri() == null ? valueSet.getUri() : codeSystem.getUri());
    valueSet.setNames(codeSystem.getNames() == null ? valueSet.getNames() : codeSystem.getNames());
    return valueSet;
  }

  public ValueSetVersion toValueSetVersion(FileProcessingCodeSystemVersion fpVersion, String valueSet, CodeSystemVersion codeSystemVersion) {
    ValueSetVersion version = new ValueSetVersion();
    version.setValueSet(valueSet);
    version.setVersion(fpVersion.getVersion());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(fpVersion.getReleaseDate());
    version.setRuleSet(new ValueSetVersionRuleSet().setRules(List.of(
        new ValueSetVersionRule()
            .setType(ValueSetVersionRuleType.include)
            .setCodeSystem(codeSystemVersion.getCodeSystem())
            .setCodeSystemVersionId(codeSystemVersion.getId())
    )));
    return version;
  }
}