package com.kodality.termserver.integration.icd10.utils;

import com.kodality.termserver.CaseSignificance;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.commons.model.constant.Language;
import com.kodality.termserver.integration.common.ImportConfiguration;
import com.kodality.termserver.integration.icd10.utils.Icd10.Class;
import com.kodality.termserver.integration.icd10.utils.Icd10.Para;
import com.kodality.termserver.integration.icd10.utils.Icd10.Reference;
import com.kodality.termserver.integration.icd10.utils.Icd10.Rubric;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Icd10Mapper {

  private static final Pattern p = Pattern.compile("^[A-Z]{1}\\d{2}+((.\\d{1,2})|(-[A-Z]{1}\\d{2}))?");

  public static List<Concept> mapConcepts(Icd10 diagnoses, ImportConfiguration configuration, List<EntityProperty> properties) {
    List<Concept> concepts = new ArrayList<>();
    concepts.add(rootConcept(configuration, properties));
    for (Class c : diagnoses.getClasses()) {
      if (c.getCode() == null || !p.matcher(c.getCode()).matches()) {
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
    Long term = properties.stream().filter(p -> p.getName().equals("term")).findFirst().map(EntityProperty::getId).orElse(null);
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
    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setCodeSystem(configuration.getCodeSystem());
    association.setAssociationType("is-a");
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(diagnosis.getSuperClass() == null || diagnosis.getSuperClass().getCode() == null ? "classification" : diagnosis.getSuperClass().getCode());
    associations.add(association);
    return associations;
  }

  public static Concept rootConcept(ImportConfiguration configuration, List<EntityProperty> properties) {
    Designation designation = new Designation();
    designation.setName("ICD-10 WHO classification");
    designation.setLanguage(Language.en);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setDesignationTypeId(properties.stream().filter(p -> p.getName().equals("term")).findFirst().map(EntityProperty::getId).orElse(null));
    designation.setStatus(PublicationStatus.active);

    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode("classification");
    version.setStatus(PublicationStatus.draft);
    version.setDesignations(List.of(designation));

    Concept concept = new Concept();
    concept.setCodeSystem(configuration.getCodeSystem());
    concept.setCode("classification");
    concept.setVersions(List.of(version));
    return concept;
  }

  private static String removeDoubleQuote(String value) {
    return value == null ? null : value.replaceAll("\".*\"", "");
  }

  private static String mapLabel(Rubric rubric) {
    String labelValue = mapLabelValue(rubric);
    if (labelValue != null) {
      return labelValue;
    }
    if (rubric.getLabel().getFragment() != null) {
      return mapLabelFragment(rubric);
    }
    if (rubric.getLabel().getPara() != null) {
      return mapLabelPara(rubric);
    }
    return null;
  }

  private static String mapLabelValue(Rubric rubric) {
    if (rubric.getLabel().getValue() == null && rubric.getLabel().getReference() == null) {
      return null;
    }
    List<String> labelValue = new ArrayList<>();
    if (rubric.getLabel().getValue() != null) {
      labelValue.add(rubric.getLabel().getValue());
    }
    if (rubric.getLabel().getReference() != null) {
      labelValue.addAll(rubric.getLabel().getReference());
    }
    return String.join(" ", labelValue);
  }

  private static String mapLabelFragment(Rubric rubric) {
    return rubric.getLabel().getFragment()
        .stream().filter(Objects::nonNull)
        .map(fragment -> {
          if (fragment.getReference() == null) {
            return fragment.getValue();
          }
          String references = fragment.getReference().stream().filter(Objects::nonNull).map(Reference::getValue).collect(Collectors.joining(" "));
          return fragment.getValue() + " " + references;
        }).collect(Collectors.joining(" "));
  }

  private static String mapLabelPara(Rubric rubric) {
    return rubric.getLabel().getPara().stream().filter(Objects::nonNull).map(Para::getValue).collect(Collectors.joining(" "));
  }
}
