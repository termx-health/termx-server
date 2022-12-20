package com.kodality.termserver.integration.ichiuz.utils;

import com.kodality.termserver.CaseSignificance;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyType;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.termserver.common.CodeSystemImportMapper;
import com.kodality.termserver.common.ImportConfiguration;
import io.micronaut.core.util.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IchiUzMapper {
  private static final String DISPLAY = "display";

  public static CodeSystem mapCodeSystem(ImportConfiguration configuration, List<IchiUz> actions) {
    CodeSystem codeSystem = CodeSystemImportMapper.mapCodeSystem(configuration, "uz-CYRL");
    codeSystem.setProperties(mapProperties());
    codeSystem.setConcepts(mapConcepts(actions, configuration));
    return codeSystem;
  }

  private static List<EntityProperty> mapProperties() {
    return List.of(new EntityProperty().setName(DISPLAY).setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName("target").setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName("action").setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName("means").setType(EntityPropertyType.string).setStatus(PublicationStatus.active)
    );
  }

  private static List<Concept> mapConcepts(List<IchiUz> actions, ImportConfiguration configuration) {
    Map<String, String> parents = new HashMap<>();
    return actions.stream().map(a -> mapConcept(a, parents, configuration)).collect(Collectors.toList());
  }

  private static Concept mapConcept(IchiUz a, Map<String, String> parents, ImportConfiguration configuration) {
    String code = null;
    String parent = null;
    if (StringUtils.isNotEmpty(a.getGroup())) {
      parents.put("group", a.getGroup());
      code = a.getGroup();
    }
    if (StringUtils.isNotEmpty(a.getSubgroup())) {
      parents.put("subgroup", a.getSubgroup());
      parent = parents.get("group");
      code = a.getSubgroup();
    }
    if (StringUtils.isNotEmpty(a.getSystem())) {
      parents.put("system", a.getSystem());
      parent = parents.get("subgroup");
      code = a.getSystem();
    }
    if (StringUtils.isNotEmpty(a.getCode())) {
      parent = parents.get("system");
      code = a.getCode();
    }

    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(code);
    version.setCodeSystem(configuration.getCodeSystem());
    version.setStatus(PublicationStatus.draft);
    version.setDesignations(mapDesignations(a));
    version.setPropertyValues(mapPropertyValues(a));
    version.setAssociations(CodeSystemImportMapper.mapAssociations(parent, "is-a", configuration));

    Concept concept = new Concept();
    concept.setCode(code);
    concept.setCodeSystem(configuration.getCodeSystem());
    concept.setVersions(List.of(version));
    return concept;
  }

  private static List<Designation> mapDesignations(IchiUz a) {
    Designation designation = new Designation();
    designation.setName(a.getDescriptorUzCyrl());
    designation.setLanguage("uz-CYRL");
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setDesignationType(DISPLAY);
    designation.setPreferred(true);
    return List.of(designation);
  }

  private static List<EntityPropertyValue> mapPropertyValues(IchiUz a) {
    EntityPropertyValue target = new EntityPropertyValue().setValue(a.getTarget()).setEntityProperty("target");
    EntityPropertyValue action = new EntityPropertyValue().setValue(a.getAction()).setEntityProperty("action");
    EntityPropertyValue means = new EntityPropertyValue().setValue(a.getMeans()).setEntityProperty("means");
    return Stream.of(target, action, means).filter(v -> StringUtils.isNotEmpty((String) v.getValue())).toList();
  }
}
