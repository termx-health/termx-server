package com.kodality.termx.icd10est.utils;

import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationKind;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemContent;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestConcept;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestProperty;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyKind;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.icd10est.utils.Icd10Est.Node;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class Icd10EstMapper {
  private static final String DISPLAY = "display";
  private static final String SYNONYM = "synonym";
  private static final String NOTICE = "notice";
  private static final String INCLUDE = "include";
  private static final String EXCLUDE = "exclude";
  private static final String IS_A = "is-a";


  public static CodeSystemImportRequest toRequest(CodeSystemImportConfiguration configuration, List<Icd10Est> diagnoses) {
    List<String> supportedLanguages = List.of(Language.en, Language.et, Language.la);

    CodeSystemImportRequest request = new CodeSystemImportRequest(configuration);
    request.getCodeSystem().setContent(CodeSystemContent.supplement).setSupportedLanguages(supportedLanguages).setHierarchyMeaning(IS_A);
    request.getVersion().setSupportedLanguages(supportedLanguages);

    request.setProperties(getProperties()).setAssociations(getAssociations());
    request.setConcepts(toConcepts(diagnoses));
    return request;
  }

  private static List<CodeSystemImportRequestProperty> getProperties() {
    return List.of(
        new CodeSystemImportRequestProperty().setName(DISPLAY).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation),
        new CodeSystemImportRequestProperty().setName(SYNONYM).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation),
        new CodeSystemImportRequestProperty().setName(NOTICE).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation),
        new CodeSystemImportRequestProperty().setName(INCLUDE).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property),
        new CodeSystemImportRequestProperty().setName(EXCLUDE).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property));
  }

  private static List<Pair<String, String>> getAssociations() {
    return List.of(Pair.of(IS_A, AssociationKind.codesystemHierarchyMeaning));
  }

  private static List<CodeSystemImportRequestConcept> toConcepts(List<Icd10Est> diagnoses) {
    List<CodeSystemImportRequestConcept> concepts = new ArrayList<>();
    diagnoses.forEach(d -> concepts.addAll(parseNodeChild(d.getChapter(), null)));
    return concepts;
  }

  private static List<CodeSystemImportRequestConcept> parseNodeChild(Node element, Node parent) {
    List<CodeSystemImportRequestConcept> concepts = new ArrayList<>();
    concepts.add(Icd10EstMapper.toConcept(element, parent));
    if (element.getChildren() != null) {
      element.getChildren().forEach(child -> concepts.addAll(parseNodeChild(child, element)));
    }
    if (element.getSub() != null) {
      element.getSub().forEach(sub -> parseNodeChild(sub, element));
    }
    return concepts;
  }


  private static CodeSystemImportRequestConcept toConcept(Node element, Node parent) {
    CodeSystemImportRequestConcept concept = new CodeSystemImportRequestConcept();
    concept.setCode(element.getCode());
    concept.setDesignations(mapDesignations(element));
    concept.setPropertyValues(mapPropertyValues(element));
    concept.setAssociations(mapAssociations(parent));
    return concept;
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

  private static List<CodeSystemAssociation> mapAssociations(Node parent) {
    if (parent == null) {
      return List.of();
    }
    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setAssociationType(IS_A);
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(parent.getCode());
    return List.of(association);
  }
}
