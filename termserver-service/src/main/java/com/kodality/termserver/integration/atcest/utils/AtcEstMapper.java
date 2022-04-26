package com.kodality.termserver.integration.atcest.utils;

import com.kodality.termserver.CaseSignificance;
import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.integration.common.ImportConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AtcEstMapper {

  public static Concept createRootConcept(ImportConfiguration configuration, List<EntityProperty> properties) {
    Designation designation = new Designation();
    designation.setName("ATC est classification");
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

  public static List<Concept> mapConcepts(List<AtcEst> atc, ImportConfiguration configuration, List<EntityProperty> properties) {
    return atc.stream().map(a -> {
      CodeSystemEntityVersion version = new CodeSystemEntityVersion();
      version.setCode(a.getCode());
      version.setStatus(PublicationStatus.draft);
      version.setDesignations(mapDesignations(a, properties));
      version.setAssociations(mapAssociations(findParent(a.getCode(), atc, 1), configuration));

      Concept concept = new Concept();
      concept.setCodeSystem(configuration.getCodeSystem());
      concept.setCode(a.getCode());
      concept.setVersions(List.of(version));
      return concept;
    }).collect(Collectors.toList());
  }

  private static List<Designation> mapDesignations(AtcEst atc, List<EntityProperty> properties) {
    Long term = properties.stream().filter(p -> p.getName().equals("term")).findFirst().map(EntityProperty::getId).orElse(null);
    Designation designation = new Designation();
    designation.setName(atc.getName());
    designation.setLanguage(Language.en);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setDesignationTypeId(term);
    designation.setPreferred(true);
    return List.of(designation);
  }

  private static List<CodeSystemAssociation> mapAssociations(String parent, ImportConfiguration configuration) {
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

  private static String findParent(String child, List<AtcEst> atc, int offset) {
    if (child.length() < 2) {
      return "classification";
    }
    Optional<AtcEst> parent = atc.stream().filter(p -> child.startsWith(p.getCode()) && p.getCode().length() == child.length() - offset).findFirst();
    if (parent.isPresent()) {
      return parent.get().getCode();
    }
    if (child.length() - offset < 1) {
      throw new IllegalStateException("Failed to find parent for ATC with code '" + child + "'");
    }
    return findParent(child, atc, offset + 1);
  }
}
