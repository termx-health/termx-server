package com.kodality.termserver.integration.atcest.utils;

import com.kodality.termserver.CaseSignificance;
import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyType;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.common.ImportConfigurationMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AtcEstMapper {

  public static CodeSystem mapCodeSystem(ImportConfiguration configuration) {
    return ImportConfigurationMapper.mapCodeSystem(configuration, Language.et);
  }

  public static List<EntityProperty> mapProperties() {
    return List.of(new EntityProperty().setName("display").setType(EntityPropertyType.string).setStatus(PublicationStatus.active));
  }

  public static List<Concept> mapConcepts(List<AtcEst> atc, ImportConfiguration configuration, List<EntityProperty> properties) {
    return atc.stream().map(a -> {
      CodeSystemEntityVersion version = new CodeSystemEntityVersion();
      version.setCode(a.getCode());
      version.setStatus(PublicationStatus.draft);
      version.setDesignations(mapDesignations(a, properties));
      version.setAssociations(ImportConfigurationMapper.mapAssociations(findParent(a.getCode(), atc, 1), "is-a", configuration));

      Concept concept = new Concept();
      concept.setCodeSystem(configuration.getCodeSystem());
      concept.setCode(a.getCode());
      concept.setVersions(List.of(version));
      return concept;
    }).collect(Collectors.toList());
  }

  private static List<Designation> mapDesignations(AtcEst atc, List<EntityProperty> properties) {
    Long term = properties.stream().filter(p -> p.getName().equals("display")).findFirst().map(EntityProperty::getId).orElse(null);
    Designation designation = new Designation();
    designation.setName(atc.getName());
    designation.setLanguage(Language.et);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setDesignationTypeId(term);
    designation.setPreferred(true);
    return List.of(designation);
  }

  private static String findParent(String child, List<AtcEst> atc, int offset) {
    if (child.length() < 2) {
      return null;
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
