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
import com.kodality.termserver.common.CodeSystemImportMapper;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.integration.icd10est.utils.Icd10Est.Node;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Icd10EstMapper {
  private static final String DISPLAY = "display";
  private static final String SYNONYM = "synonym";
  private static final String NOTICE = "notice";
  private static final String INCLUDE = "include";
  private static final String EXCLUDE = "exclude";


  public static CodeSystem mapCodeSystem(ImportConfiguration configuration, List<Icd10Est> diagnoses) {
    CodeSystem codeSystem = CodeSystemImportMapper.mapCodeSystem(configuration, Language.et);
    codeSystem.setProperties(mapProperties());
    codeSystem.setConcepts(parseDiagnoses(diagnoses, configuration));
    return codeSystem;
  }

  private static List<EntityProperty> mapProperties() {
    return List.of(
        new EntityProperty().setName(DISPLAY).setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName(SYNONYM).setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName(NOTICE).setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName(INCLUDE).setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName(EXCLUDE).setType(EntityPropertyType.string).setStatus(PublicationStatus.active));
  }

  private static List<Concept> parseDiagnoses(List<Icd10Est> diagnoses, ImportConfiguration configuration) {
    log.info("Mapping diagnoses to concepts...");
    List<Concept> concepts = new ArrayList<>();
    diagnoses.forEach(d -> concepts.addAll(parseNodeChild(d.getChapter(), null, configuration)));
    return concepts;
  }

  private static List<Concept> parseNodeChild(Node element, Node parent, ImportConfiguration configuration) {
    List<Concept> concepts = new ArrayList<>();
    concepts.add(Icd10EstMapper.mapConcept(element, parent, configuration));
    if (element.getChildren() != null) {
      element.getChildren().forEach(child -> concepts.addAll(parseNodeChild(child, element, configuration)));
    }
    if (element.getSub() != null) {
      element.getSub().forEach(sub -> parseNodeChild(sub, element, configuration));
    }
    return concepts;
  }


  private static Concept mapConcept(Node element, Node parent, ImportConfiguration configuration) {
    Concept concept = new Concept();
    concept.setCodeSystem(configuration.getCodeSystem());
    concept.setCode(element.getCode());
    concept.setVersions(List.of(mapConceptVersion(element, parent, configuration)));
    return concept;
  }

  private static CodeSystemEntityVersion mapConceptVersion(Node element, Node parent, ImportConfiguration configuration) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(element.getCode());
    version.setStatus(PublicationStatus.draft);
    version.setDesignations(mapDesignations(element));
    version.setPropertyValues(mapPropertyValues(element));
    version.setAssociations(mapAssociations(parent, configuration));
    return version;
  }

  private static List<Designation> mapDesignations(Node element) {
    List<Designation> designations = new ArrayList<>();
   element.getObject().forEach(obj -> {
      boolean main = obj.getHidden() != null && 1 == obj.getHidden();
      if (StringUtils.isNotEmpty(obj.getNameEst())) {
        designations.add(mapDesignation(obj.getNameEst(), Language.et, main ? DISPLAY : SYNONYM, main));
      }
      if (StringUtils.isNotEmpty(obj.getNameEng())) {
        designations.add(mapDesignation(obj.getNameEng(), Language.en, main ? DISPLAY : SYNONYM, main));
      }
      if (StringUtils.isNotEmpty(obj.getNameLat())) {
        designations.add(mapDesignation(obj.getNameLat(), Language.la, main ? DISPLAY : SYNONYM, main));
      }
    });
    return designations;
  }

  private static Designation mapDesignation(String name, String lang, String type, boolean preferred) {
    Designation designation = new Designation();
    designation.setName(name);
    designation.setLanguage(lang);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setDesignationType(type);
    designation.setPreferred(preferred);
    return designation;
  }

  private static List<EntityPropertyValue> mapPropertyValues(Node element) {
    List<EntityPropertyValue> values = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(element.getExclude())) {
      element.getExclude().forEach(exclude -> {
        EntityPropertyValue val = new EntityPropertyValue();
        val.setValue(exclude.getCode() + " " + exclude.getText());
        val.setEntityProperty(EXCLUDE);
        values.add(val);
      });
    }
    if (CollectionUtils.isNotEmpty(element.getInclude())) {
      element.getInclude().forEach(include -> {
        EntityPropertyValue val = new EntityPropertyValue();
        val.setValue(include.getText());
        val.setEntityProperty(INCLUDE);
        values.add(val);
      });
    }
    if (CollectionUtils.isNotEmpty(element.getNotice())) {
      element.getNotice().forEach(notice -> {
        EntityPropertyValue val = new EntityPropertyValue();
        val.setValue(notice);
        val.setEntityProperty(NOTICE);
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
    return CodeSystemImportMapper.mapAssociations(parent.getCode(), "is-a", configuration);
  }
}
