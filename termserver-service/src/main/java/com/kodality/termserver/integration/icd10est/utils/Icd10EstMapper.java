package com.kodality.termserver.integration.icd10est.utils;

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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Icd10EstMapper {

  public static CodeSystem mapCodeSystem(ImportConfiguration configuration) {
    return ImportConfigurationMapper.mapCodeSystem(configuration, Language.et);
  }

  public static List<EntityProperty> mapProperties() {
    return List.of(
        new EntityProperty().setName("display").setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName("synonym").setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName("notice").setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName("include").setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName("exclude").setType(EntityPropertyType.string).setStatus(PublicationStatus.active));
  }


  public static Concept mapConcept(Node element, Node parent, ImportConfiguration configuration, List<EntityProperty> properties) {
    Concept concept = new Concept();
    concept.setCodeSystem(configuration.getCodeSystem());
    concept.setCode(element.getCode());
    concept.setVersions(List.of(mapConceptVersion(element, parent, configuration, properties)));
    return concept;
  }

  private static CodeSystemEntityVersion mapConceptVersion(Node element, Node parent, ImportConfiguration configuration, List<EntityProperty> properties) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(element.getCode());
    version.setStatus(PublicationStatus.draft);
    version.setDesignations(mapDesignations(element, properties));
    version.setPropertyValues(mapPropertyValues(element, properties));
    version.setAssociations(mapAssociations(parent, configuration));
    return version;
  }

  private static List<Designation> mapDesignations(Node element, List<EntityProperty> properties) {
    List<Designation> designations = new ArrayList<>();
    Long term = properties.stream().filter(p -> p.getName().equals("display")).findFirst().map(EntityProperty::getId).orElse(null);
    Long synonym = properties.stream().filter(p -> p.getName().equals("synonym")).findFirst().map(EntityProperty::getId).orElse(null);
    element.getObject().forEach(obj -> {
      boolean main = obj.getHidden() != null && 1 == obj.getHidden();
      if (StringUtils.isNotEmpty(obj.getNameEst())) {
        designations.add(mapDesignation(obj.getNameEst(), Language.et, main ? term : synonym, main));
      }
      if (StringUtils.isNotEmpty(obj.getNameEng())) {
        designations.add(mapDesignation(obj.getNameEng(), Language.en, main ? term : synonym, main));
      }
      if (StringUtils.isNotEmpty(obj.getNameLat())) {
        designations.add(mapDesignation(obj.getNameLat(), Language.la, main ? term : synonym, main));
      }
    });
    return designations;
  }

  private static Designation mapDesignation(String name, String lang, Long typeId, boolean preferred) {
    Designation designation = new Designation();
    designation.setName(name);
    designation.setLanguage(lang);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setDesignationTypeId(typeId);
    designation.setPreferred(preferred);
    return designation;
  }

  private static List<EntityPropertyValue> mapPropertyValues(Node element, List<EntityProperty> properties) {
    List<EntityPropertyValue> values = new ArrayList<>();
    Long excludeProperty = properties.stream().filter(p -> p.getName().equals("exclude")).findFirst().map(EntityProperty::getId).orElse(null);
    Long includeProperty = properties.stream().filter(p -> p.getName().equals("include")).findFirst().map(EntityProperty::getId).orElse(null);
    Long noticeProperty = properties.stream().filter(p -> p.getName().equals("notice")).findFirst().map(EntityProperty::getId).orElse(null);

    if (CollectionUtils.isNotEmpty(element.getExclude())) {
      element.getExclude().forEach(exclude -> {
        EntityPropertyValue val = new EntityPropertyValue();
        val.setValue(exclude.getCode() + " " + exclude.getText());
        val.setEntityPropertyId(excludeProperty);
        values.add(val);
      });
    }
    if (CollectionUtils.isNotEmpty(element.getInclude())) {
      element.getInclude().forEach(include -> {
        EntityPropertyValue val = new EntityPropertyValue();
        val.setValue(include.getText());
        val.setEntityPropertyId(includeProperty);
        values.add(val);
      });
    }
    if (CollectionUtils.isNotEmpty(element.getNotice())) {
      element.getNotice().forEach(notice -> {
        EntityPropertyValue val = new EntityPropertyValue();
        val.setValue(notice);
        val.setEntityPropertyId(noticeProperty);
        values.add(val);
      });
    }
    return values;
  }

  private static List<CodeSystemAssociation> mapAssociations(Node parent, ImportConfiguration configuration) {
    List<CodeSystemAssociation> associations = new ArrayList<>();
    if (parent == null) {
      return associations;
    }
    return ImportConfigurationMapper.mapAssociations(parent.getCode(), "is-a", configuration);
  }
}
