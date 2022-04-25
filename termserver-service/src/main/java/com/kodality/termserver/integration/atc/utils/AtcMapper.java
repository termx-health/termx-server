package com.kodality.termserver.integration.atc.utils;

import com.kodality.termserver.CaseSignificance;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.commons.model.constant.Language;
import com.kodality.termserver.integration.common.ImportConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

public class AtcMapper {

  public static Concept createRootConcept(ImportConfiguration configuration, List<EntityProperty> properties) {
    Designation designation = new Designation();
    designation.setName("ATC classification");
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


  public static List<Concept> mapConcepts(Map<String, String> atc, ImportConfiguration configuration, List<EntityProperty> properties) {
    return atc.entrySet().stream().map(e -> {
      CodeSystemEntityVersion version = new CodeSystemEntityVersion();
      version.setCode(e.getKey());
      version.setStatus(PublicationStatus.draft);
      version.setDesignations(mapDesignations(e, properties));
      version.setAssociations(mapNodeAssociations(findParent(e.getKey(), atc, 1), configuration));

      Concept concept = new Concept();
      concept.setCodeSystem(configuration.getCodeSystem());
      concept.setCode(e.getKey());
      concept.setVersions(List.of(version));
      return concept;

    }).collect(Collectors.toList());
  }

  private static List<Designation> mapDesignations(Entry<String, String> atc, List<EntityProperty> properties) {
    Long term = properties.stream().filter(p -> p.getName().equals("term")).findFirst().map(EntityProperty::getId).orElse(null);
    Designation designation = new Designation();
    designation.setName(atc.getValue());
    designation.setLanguage(Language.en);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setDesignationTypeId(term);
    designation.setPreferred(true);
    return List.of(designation);
  }

  private static List<CodeSystemAssociation> mapNodeAssociations(String parent, ImportConfiguration configuration) {
    List<CodeSystemAssociation> associations = new ArrayList<>();
    if (parent == null) {
      return associations;
    }

    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setCodeSystem(configuration.getCodeSystem());
    association.setAssociationType("is-a");
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(parent);
    associations.add(association);
    return associations;
  }

  private static String findParent(String child, Map<String, String> atc, int offset) {
    if (child.length() < 2) {
      return "classification";
    }
    Optional<Entry<String, String>> parent = atc.entrySet().stream().filter(p -> child.startsWith(p.getKey()) && p.getKey().length() == child.length() - offset).findFirst();
    if (parent.isPresent()) {
      return parent.get().getKey();
    }
    if (child.length() - offset < 1) {
      throw new IllegalStateException("Failed to find parent for ATC with code '" + child + "'");
    }
    return findParent(child, atc, offset + 1);
  }

}
