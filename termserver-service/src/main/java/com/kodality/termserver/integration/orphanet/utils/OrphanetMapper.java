package com.kodality.termserver.integration.orphanet.utils;

import com.kodality.termserver.CaseSignificance;
import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyType;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.common.ImportConfigurationMapper;
import com.kodality.termserver.integration.icd10est.utils.Icd10Est.Node;
import com.kodality.termserver.integration.orphanet.utils.OrphanetClassificationList.ClassificationNode;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.ArrayList;
import java.util.List;

public class OrphanetMapper {
  public static CodeSystem mapCodeSystem(ImportConfiguration configuration) {
    return ImportConfigurationMapper.mapCodeSystem(configuration, Language.et);
  }

  public static List<EntityProperty> mapProperties() {
    return List.of(
        new EntityProperty().setName("display").setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName("type").setType(EntityPropertyType.string).setStatus(PublicationStatus.active));
  }

  public static Concept mapConcept(ClassificationNode node, ClassificationNode parent, ImportConfiguration configuration, List<EntityProperty> properties) {
    Concept concept = new Concept();
    concept.setCodeSystem(configuration.getCodeSystem());
    concept.setCode(node.getDisorder().getOrphaCode());
    concept.setVersions(List.of(mapConceptVersion(node, parent, configuration, properties)));
    return concept;
  }

  private static CodeSystemEntityVersion mapConceptVersion(ClassificationNode node, ClassificationNode parent, ImportConfiguration configuration, List<EntityProperty> properties) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(node.getDisorder().getOrphaCode());
    version.setStatus(PublicationStatus.draft);
    version.setDesignations(mapDesignations(node, properties));
    version.setPropertyValues(mapPropertyValues(node, properties));
    version.setAssociations(mapAssociations(parent, configuration));
    return version;
  }

  private static List<Designation> mapDesignations(ClassificationNode node, List<EntityProperty> properties) {
    Long typeId = properties.stream().filter(p -> p.getName().equals("display")).findFirst().map(EntityProperty::getId).orElse(null);

    Designation designation = new Designation();
    designation.setName(node.getDisorder().getName().getValue());
    designation.setLanguage(Language.en);
    designation.setDesignationTypeId(typeId);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setPreferred(true);
    return List.of(designation);
  }

  private static List<EntityPropertyValue> mapPropertyValues(ClassificationNode node, List<EntityProperty> properties) {
    if (node.getDisorder().getDisorderType() == null || node.getDisorder().getDisorderType().getCategory() == null) {
      return List.of();
    }
    Long propertyId = properties.stream().filter(p -> p.getName().equals("type")).findFirst().map(EntityProperty::getId).orElse(null);
    EntityPropertyValue value = new EntityPropertyValue();
    value.setValue(node.getDisorder().getDisorderType().getCategory().getValue());
    value.setEntityPropertyId(propertyId);
    return List.of(value);
  }

  private static List<CodeSystemAssociation> mapAssociations(ClassificationNode parent, ImportConfiguration configuration) {
    List<CodeSystemAssociation> associations = new ArrayList<>();
    if (parent == null) {
      return associations;
    }
    return ImportConfigurationMapper.mapAssociations(parent.getDisorder().getOrphaCode(), "is-a", configuration);
  }
}
