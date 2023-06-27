package com.kodality.termserver.icd10.utils;

import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.association.AssociationKind;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemContent;
import com.kodality.termserver.ts.codesystem.CodeSystemImportConfiguration;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestConcept;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestProperty;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityPropertyKind;
import com.kodality.termserver.ts.codesystem.EntityPropertyType;
import com.kodality.termserver.icd10.utils.Icd10.Class;
import com.kodality.termserver.icd10.utils.Icd10.Fragment;
import com.kodality.termserver.icd10.utils.Icd10.Para;
import com.kodality.termserver.icd10.utils.Icd10.Rubric;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class Icd10Mapper {
  private static final String DISPLAY = "display";
  private static final String SYNONYM = "synonym";
  private static final String IS_A = "is-a";

  public static CodeSystemImportRequest toRequest(CodeSystemImportConfiguration configuration, Icd10 diagnoses) {
    List<String> supportedLanguages = List.of(Language.en);

    CodeSystemImportRequest request = new CodeSystemImportRequest(configuration);
    request.getCodeSystem().setContent(CodeSystemContent.complete).setSupportedLanguages(supportedLanguages).setHierarchyMeaning(IS_A);
    request.getVersion().setSupportedLanguages(supportedLanguages);

    request.setProperties(getProperties()).setAssociations(getAssociations());
    request.setConcepts(toConcepts(diagnoses));
    return request;
  }

  private static List<CodeSystemImportRequestProperty> getProperties() {
    return List.of(
        new CodeSystemImportRequestProperty().setName(DISPLAY).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation),
        new CodeSystemImportRequestProperty().setName(SYNONYM).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation));
  }

  private static List<Pair<String, String>> getAssociations() {
    return List.of(Pair.of(IS_A, AssociationKind.codesystemHierarchyMeaning));
  }

  private static List<CodeSystemImportRequestConcept> toConcepts(Icd10 diagnoses) {
    List<CodeSystemImportRequestConcept> concepts = new ArrayList<>();
    for (Class c : diagnoses.getClasses()) {
      if (c.getCode() == null) {
        continue;
      }
      concepts.add(mapConcept(c));
    }
    return concepts;
  }

  private static CodeSystemImportRequestConcept mapConcept(Class diagnosis) {
    CodeSystemImportRequestConcept concept = new CodeSystemImportRequestConcept();
    concept.setCode(diagnosis.getCode());
    concept.setDesignations(mapDesignations(diagnosis));
    concept.setAssociations(mapAssociations(diagnosis));
    return concept;
  }

  private static List<Designation> mapDesignations(Class diagnosis) {
    List<Designation> designations = new ArrayList<>();
    diagnosis.getRubrics().forEach(rubric -> {
      boolean main = "preferred".equals(rubric.getKind());
      designations.add(mapDesignation(removeDoubleQuote(mapLabel(rubric)), main ? DISPLAY : SYNONYM, main));
    });
    return designations;
  }

  private static Designation mapDesignation(String name, String type, boolean preferred) {
    Designation designation = new Designation();
    designation.setName(name);
    designation.setLanguage(Language.en);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setDesignationType(type);
    designation.setPreferred(preferred);
    return designation;
  }

  private static List<CodeSystemAssociation> mapAssociations(Class diagnosis) {
    if (diagnosis.getSuperClass() == null) {
      return List.of();
    }
    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setAssociationType(IS_A);
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(diagnosis.getSuperClass().getCode());
    return List.of(association);
  }

  private static String removeDoubleQuote(String value) {
    return value == null ? null : value.replaceAll("\".*\"", "");
  }

  private static String mapLabel(Rubric rubric) {
    String labelValue = rubric.getLabel().getValue() == null ? "" : rubric.getLabel().getValue();
    if (rubric.getLabel().getFragment() != null) {
      String fragment = mapLabelFragment(rubric);
      labelValue = fragment == null ? labelValue : labelValue + " " + fragment;
    }
    if (rubric.getLabel().getPara() != null) {
      String para = mapLabelPara(rubric);
      labelValue = para == null ? labelValue : labelValue + " " + para;
    }
    return labelValue;
  }

  private static String mapLabelFragment(Rubric rubric) {
    return rubric.getLabel().getFragment()
        .stream().filter(Objects::nonNull)
        .map(Fragment::getValue)
        .collect(Collectors.joining(" "));
  }

  private static String mapLabelPara(Rubric rubric) {
    return rubric.getLabel().getPara().stream().filter(Objects::nonNull).map(Para::getValue).collect(Collectors.joining(" "));
  }
}
