package com.kodality.termserver.integration.orphanet.utils;

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
import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.common.CodeSystemImportMapper;
import com.kodality.termserver.integration.orphanet.utils.OrphanetClassificationList.ClassificationNode;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrphanetMapper {
  private static final String DISPLAY = "display";
  private static final String TYPE = "type";

  public static CodeSystem mapCodeSystem(ImportConfiguration configuration, List<ClassificationNode> nodes) {
    CodeSystem codeSystem = CodeSystemImportMapper.mapCodeSystem(configuration, Language.et);
    codeSystem.setProperties(mapProperties());
    codeSystem.setConcepts(parseNodes(nodes, configuration));
    return codeSystem;
  }

  private static List<EntityProperty> mapProperties() {
    return List.of(
        new EntityProperty().setName(DISPLAY).setType(EntityPropertyType.string).setStatus(PublicationStatus.active),
        new EntityProperty().setName(TYPE).setType(EntityPropertyType.string).setStatus(PublicationStatus.active));
  }

  private static List<Concept> parseNodes(List<ClassificationNode> nodes, ImportConfiguration configuration) {
    log.info("Mapping nodes to concepts...");
    List<Concept> concepts = new ArrayList<>();
    nodes.forEach(n -> concepts.addAll(parseNodeChild(n, null, configuration)));
    return concepts;
  }

  private static List<Concept> parseNodeChild(ClassificationNode node, ClassificationNode parent, ImportConfiguration configuration) {
    List<Concept> concepts = new ArrayList<>();
    concepts.add(OrphanetMapper.mapConcept(node, parent, configuration));
    if (node.getClassificationNodeChildList() != null && node.getClassificationNodeChildList().getClassificationNodes() != null) {
      node.getClassificationNodeChildList().getClassificationNodes().forEach(child -> concepts.addAll(parseNodeChild(child, node, configuration)));
    }
    return concepts;
  }

  private static Concept mapConcept(ClassificationNode node, ClassificationNode parent, ImportConfiguration configuration) {
    Concept concept = new Concept();
    concept.setCodeSystem(configuration.getCodeSystem());
    concept.setCode(node.getDisorder().getOrphaCode());
    concept.setVersions(List.of(mapConceptVersion(node, parent, configuration)));
    return concept;
  }

  private static CodeSystemEntityVersion mapConceptVersion(ClassificationNode node, ClassificationNode parent, ImportConfiguration configuration) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(node.getDisorder().getOrphaCode());
    version.setCodeSystem(configuration.getCodeSystem());
    version.setStatus(PublicationStatus.draft);
    version.setDesignations(mapDesignations(node));
    version.setPropertyValues(mapPropertyValues(node));
    version.setAssociations(mapAssociations(parent, configuration));
    return version;
  }

  private static List<Designation> mapDesignations(ClassificationNode node) {
    Designation designation = new Designation();
    designation.setName(node.getDisorder().getName().getValue());
    designation.setLanguage(Language.en);
    designation.setDesignationType(DISPLAY);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setPreferred(true);
    return List.of(designation);
  }

  private static List<EntityPropertyValue> mapPropertyValues(ClassificationNode node) {
    if (node.getDisorder().getDisorderType() == null || node.getDisorder().getDisorderType().getCategory() == null) {
      return List.of();
    }
    EntityPropertyValue value = new EntityPropertyValue();
    value.setValue(node.getDisorder().getDisorderType().getCategory().getValue());
    value.setEntityProperty(TYPE);
    return List.of(value);
  }

  private static List<CodeSystemAssociation> mapAssociations(ClassificationNode parent, ImportConfiguration configuration) {
    List<CodeSystemAssociation> associations = new ArrayList<>();
    if (parent == null) {
      return associations;
    }
    return CodeSystemImportMapper.mapAssociations(parent.getDisorder().getOrphaCode(), "is-a", configuration);
  }
}
