package com.kodality.termserver.ichiuz.utils;

import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.association.AssociationKind;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemContent;
import com.kodality.termserver.ts.codesystem.CodeSystemImportConfiguration;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestConcept;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityPropertyType;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
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
    request.getCodeSystem().setContent(CodeSystemContent.supplement).setSupportedLanguages(supportedLanguages);
    request.getVersion().setSupportedLanguages(supportedLanguages);

    request.setProperties(getProperties()).setAssociations(getAssociations());
    request.setConcepts(toConcepts(actions));
    return request;
  }

  private static List<Pair<String, String>> getProperties() {
    return List.of(
        Pair.of(DISPLAY, EntityPropertyType.string),
        Pair.of(TARGET, EntityPropertyType.string),
        Pair.of(ACTION, EntityPropertyType.string),
        Pair.of(MEANS, EntityPropertyType.string));
  }

  private static List<Pair<String, String>> getAssociations() {
    return List.of(Pair.of(IS_A, AssociationKind.codesystemHierarchyMeaning));
  }

  private static List<CodeSystemImportRequestConcept> toConcepts(List<IchiUz> actions) {
    Map<String, String> parents = new HashMap<>();
    return actions.stream().map(a -> mapConcept(a, parents)).collect(Collectors.toList());
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
