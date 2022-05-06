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
import com.kodality.termserver.common.ImportConfigurationMapper;
import com.kodality.termserver.integration.icd10.utils.Icd10.Class;
import com.kodality.termserver.integration.icd10.utils.Icd10.Fragment;
import com.kodality.termserver.integration.icd10.utils.Icd10.Para;
import com.kodality.termserver.integration.icd10.utils.Icd10.Rubric;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Icd10Mapper {

  public static CodeSystem mapCodeSystem(ImportConfiguration configuration) {
    return ImportConfigurationMapper.mapCodeSystem(configuration, Language.en);
  }

  public static List<EntityProperty> mapProperties() {
    return List.of(
        new EntityProperty().setName("display").setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName("synonym").setType(EntityPropertyType.string).setStatus(PublicationStatus.active));
  }

  public static List<Concept> mapConcepts(Icd10 diagnoses, ImportConfiguration configuration, List<EntityProperty> properties) {
    List<Concept> concepts = new ArrayList<>();
    for (Class c : diagnoses.getClasses()) {
      if (c.getCode() == null) {
        continue;
      }
      concepts.add(mapConcept(c, configuration, properties));
    }
    return concepts;
  }

  public static Concept mapConcept(Class diagnosis, ImportConfiguration configuration, List<EntityProperty> properties) {
    Concept concept = new Concept();
    concept.setCodeSystem(configuration.getCodeSystem());
    concept.setCode(diagnosis.getCode());
    concept.setVersions(List.of(mapConceptVersion(diagnosis, configuration, properties)));
    return concept;
  }

  private static CodeSystemEntityVersion mapConceptVersion(Class diagnosis, ImportConfiguration configuration, List<EntityProperty> properties) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(diagnosis.getCode());
    version.setStatus(PublicationStatus.draft);
    version.setDesignations(mapDesignations(diagnosis, properties));
    version.setAssociations(mapAssociations(diagnosis, configuration));
    return version;
  }

  private static List<Designation> mapDesignations(Class diagnosis, List<EntityProperty> properties) {
    List<Designation> designations = new ArrayList<>();
    Long term = properties.stream().filter(p -> p.getName().equals("display")).findFirst().map(EntityProperty::getId).orElse(null);
    Long synonym = properties.stream().filter(p -> p.getName().equals("synonym")).findFirst().map(EntityProperty::getId).orElse(null);
    diagnosis.getRubrics().forEach(rubric -> {
      boolean main = "preferred".equals(rubric.getKind());
      designations.add(mapDesignation(removeDoubleQuote(mapLabel(rubric)), main ? term : synonym, main));
    });
    return designations;
  }

  private static Designation mapDesignation(String name, Long typeId, boolean preferred) {
    Designation designation = new Designation();
    designation.setName(name);
    designation.setLanguage(Language.en);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setDesignationTypeId(typeId);
    designation.setPreferred(preferred);
    return designation;
  }

  private static List<CodeSystemAssociation> mapAssociations(Class diagnosis, ImportConfiguration configuration) {
    List<CodeSystemAssociation> associations = new ArrayList<>();
    if (diagnosis.getSuperClass() == null) {
      return associations;
    }
    return ImportConfigurationMapper.mapAssociations(diagnosis.getSuperClass().getCode(), "is-a", configuration);
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
