package com.kodality.termserver.integration.icd10.utils;

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
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.common.CodeSystemImportMapper;
import com.kodality.termserver.integration.icd10.utils.Icd10.Class;
import com.kodality.termserver.integration.icd10.utils.Icd10.Fragment;
import com.kodality.termserver.integration.icd10.utils.Icd10.Para;
import com.kodality.termserver.integration.icd10.utils.Icd10.Rubric;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Icd10Mapper {
  private static final String DISPLAY = "display";
  private static final String SYNONYM = "synonym";

  public static CodeSystem mapCodeSystem(ImportConfiguration configuration, Icd10 diagnoses) {
    CodeSystem codeSystem =  CodeSystemImportMapper.mapCodeSystem(configuration, Language.en);
    codeSystem.setProperties(mapProperties());
    codeSystem.setConcepts(mapConcepts(diagnoses, configuration));
    return codeSystem;
  }

  private static List<EntityProperty> mapProperties() {
    return List.of(
        new EntityProperty().setName(DISPLAY).setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName(SYNONYM).setType(EntityPropertyType.string).setStatus(PublicationStatus.active));
  }

  private static List<Concept> mapConcepts(Icd10 diagnoses, ImportConfiguration configuration) {
    List<Concept> concepts = new ArrayList<>();
    for (Class c : diagnoses.getClasses()) {
      if (c.getCode() == null) {
        continue;
      }
      concepts.add(mapConcept(c, configuration));
    }
    return concepts;
  }

  private static Concept mapConcept(Class diagnosis, ImportConfiguration configuration) {
    Concept concept = new Concept();
    concept.setCodeSystem(configuration.getCodeSystem());
    concept.setCode(diagnosis.getCode());
    concept.setVersions(List.of(mapConceptVersion(diagnosis, configuration)));
    return concept;
  }

  private static CodeSystemEntityVersion mapConceptVersion(Class diagnosis, ImportConfiguration configuration) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(diagnosis.getCode());
    version.setCodeSystem(configuration.getCodeSystem());
    version.setStatus(PublicationStatus.draft);
    version.setDesignations(mapDesignations(diagnosis));
    version.setAssociations(mapAssociations(diagnosis, configuration));
    return version;
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

  private static List<CodeSystemAssociation> mapAssociations(Class diagnosis, ImportConfiguration configuration) {
    List<CodeSystemAssociation> associations = new ArrayList<>();
    if (diagnosis.getSuperClass() == null) {
      return associations;
    }
    return CodeSystemImportMapper.mapAssociations(diagnosis.getSuperClass().getCode(), "is-a", configuration);
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
