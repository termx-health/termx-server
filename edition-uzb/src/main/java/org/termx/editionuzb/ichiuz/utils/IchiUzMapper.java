package org.termx.editionuzb.ichiuz.utils;

import org.termx.ts.CaseSignificance;
import org.termx.ts.Language;
import org.termx.ts.PublicationStatus;
import org.termx.ts.association.AssociationKind;
import org.termx.ts.codesystem.CodeSystemAssociation;
import org.termx.ts.codesystem.CodeSystemContent;
import org.termx.ts.codesystem.CodeSystemImportConfiguration;
import org.termx.ts.codesystem.CodeSystemImportRequest;
import org.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestConcept;
import org.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestProperty;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.EntityPropertyKind;
import org.termx.ts.codesystem.EntityPropertyType;
import org.termx.ts.codesystem.EntityPropertyValue;
import io.micronaut.core.util.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class IchiUzMapper {
  private static final String DISPLAY = "display";
  private static final String TARGET = "target";
  private static final String ACTION = "action";
  private static final String MEANS = "means";
  private static final String IS_A = "is-a";

  public static CodeSystemImportRequest toRequest(CodeSystemImportConfiguration configuration, List<IchiUz> actions) {
    List<String> supportedLanguages = List.of(Language.en, Language.uz_cyrl, Language.uz_latn, Language.ru);

    CodeSystemImportRequest request = new CodeSystemImportRequest(configuration);
    request.getCodeSystem().setContent(CodeSystemContent.supplement).setSupportedLanguages(supportedLanguages).setHierarchyMeaning(IS_A);
    request.getVersion().setSupportedLanguages(supportedLanguages);

    request.setProperties(getProperties()).setAssociations(getAssociations());
    request.setConcepts(toConcepts(actions));
    return request;
  }

  private static List<CodeSystemImportRequestProperty> getProperties() {
    return List.of(
        new CodeSystemImportRequestProperty().setName(DISPLAY).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation),
        new CodeSystemImportRequestProperty().setName(TARGET).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property),
        new CodeSystemImportRequestProperty().setName(ACTION).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property),
        new CodeSystemImportRequestProperty().setName(MEANS).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property));
  }

  private static List<Pair<String, String>> getAssociations() {
    return List.of(Pair.of(IS_A, AssociationKind.codesystemHierarchyMeaning));
  }

  private static List<CodeSystemImportRequestConcept> toConcepts(List<IchiUz> actions) {
    Map<String, String> parents = new HashMap<>();
    return actions.stream().map(a -> mapConcept(a, parents)).toList();
  }

  private static CodeSystemImportRequestConcept mapConcept(IchiUz a, Map<String, String> parents) {
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

    CodeSystemImportRequestConcept concept = new CodeSystemImportRequestConcept();
    concept.setCode(code);
    concept.setDesignations(mapDesignations(a));
    concept.setPropertyValues(mapPropertyValues(a));
    concept.setAssociations(mapAssociations(parent));
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

  private static List<CodeSystemAssociation> mapAssociations(String parent) {
    if (parent == null) {
      return List.of();
    }
    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setAssociationType(IS_A);
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(parent);
    return List.of(association);
  }
}
