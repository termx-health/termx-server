package com.kodality.termserver.orphanet.utils;

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
import com.kodality.termserver.orphanet.utils.OrphanetClassificationList.ClassificationNode;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class OrphanetMapper {
  private static final String DISPLAY = "display";
  private static final String TYPE = "type";
  private static final String IS_A = "is-a";

  public static CodeSystemImportRequest toRequest(CodeSystemImportConfiguration configuration, List<ClassificationNode> nodes) {
    List<String> supportedLanguages = List.of(Language.en);

    CodeSystemImportRequest request = new CodeSystemImportRequest(configuration);
    request.getCodeSystem().setContent(CodeSystemContent.complete).setSupportedLanguages(supportedLanguages);
    request.getVersion().setSupportedLanguages(supportedLanguages);

    request.setProperties(getProperties()).setAssociations(getAssociations());
    request.setConcepts(toConcepts(nodes));
    return request;
  }

  private static List<Pair<String, String>> getProperties() {
    return List.of(
        Pair.of(DISPLAY, EntityPropertyType.string),
        Pair.of(TYPE, EntityPropertyType.string));
  }

  private static List<Pair<String, String>> getAssociations() {
    return List.of(Pair.of(IS_A, AssociationKind.codesystemHierarchyMeaning));
  }

  private static List<CodeSystemImportRequestConcept> toConcepts(List<ClassificationNode> nodes) {
    List<CodeSystemImportRequestConcept> concepts = new ArrayList<>();
    nodes.forEach(n -> concepts.addAll(parseNodeChild(n, null)));
    return concepts;
  }

  private static List<CodeSystemImportRequestConcept> parseNodeChild(ClassificationNode node, ClassificationNode parent) {
    List<CodeSystemImportRequestConcept> concepts = new ArrayList<>();
    concepts.add(OrphanetMapper.mapConcept(node, parent));
    if (node.getClassificationNodeChildList() != null && node.getClassificationNodeChildList().getClassificationNodes() != null) {
      node.getClassificationNodeChildList().getClassificationNodes().forEach(child -> concepts.addAll(parseNodeChild(child, node)));
    }
    return concepts;
  }

  private static CodeSystemImportRequestConcept mapConcept(ClassificationNode node, ClassificationNode parent) {
    CodeSystemImportRequestConcept concept = new CodeSystemImportRequestConcept();
    concept.setCode(node.getDisorder().getOrphaCode());
    concept.setDesignations(mapDesignations(node));
    concept.setPropertyValues(mapPropertyValues(node));
    concept.setAssociations(mapAssociations(parent));
    return concept;
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

  private static List<CodeSystemAssociation> mapAssociations(ClassificationNode parent) {
    if (parent == null) {
      return List.of();
    }
    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setAssociationType(IS_A);
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(parent.getDisorder().getOrphaCode());
    return List.of(association);
  }
}
