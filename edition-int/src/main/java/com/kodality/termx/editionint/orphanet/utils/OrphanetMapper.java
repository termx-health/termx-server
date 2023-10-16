package com.kodality.termx.editionint.orphanet.utils;

import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationKind;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemContent;
import com.kodality.termx.ts.codesystem.CodeSystemImportConfiguration;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestConcept;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestProperty;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyKind;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.editionint.orphanet.utils.ClassificationList.ClassificationNode;
import com.kodality.termx.ts.codesystem.EntityPropertyValue.EntityPropertyValueCodingValue;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class OrphanetMapper {
  private static final String DISPLAY = "display";
  private static final String SYNONYM = "synonym";
  private static final String DEFINITION = "definition";
  private static final String DISORDER_TYPE = "disorder-type";
  private static final String DISORDER_ID = "disorder-id";
  private static final String EXPERT_LINK = "expert-link";
  private static final String IS_A = "is-a";
  private static final String RELATED = "relatedto";

  public static CodeSystemImportRequest toRequest(CodeSystemImportConfiguration configuration, ClassificationList classificationList) {
    CodeSystemImportRequest request = toRequest(configuration);

    List<ClassificationNode> nodes = classificationList.getClassifications().get(0).getClassificationNodeRootList().getClassificationNodes();
    request.setConcepts(toConcepts(nodes));
    return request;
  }

  public static CodeSystemImportRequest toRequest(CodeSystemImportConfiguration configuration, DisorderList disorderList) {
    CodeSystemImportRequest request = toRequest(configuration);
    request.setConcepts(disorderList.getDisorders().stream().map(OrphanetMapper::mapConcept).toList());
    return request;
  }

  private static CodeSystemImportRequest toRequest(CodeSystemImportConfiguration configuration) {
    List<String> supportedLanguages = List.of(Language.en);

    CodeSystemImportRequest request = new CodeSystemImportRequest(configuration);
    request.getCodeSystem().setContent(CodeSystemContent.complete).setSupportedLanguages(supportedLanguages).setHierarchyMeaning(IS_A);
    request.getVersion().setSupportedLanguages(supportedLanguages);
    request.setProperties(getProperties()).setAssociations(getAssociations());
    request.setActivate(PublicationStatus.active.equals(configuration.getStatus()));
    return request;
  }

  private static List<CodeSystemImportRequestProperty> getProperties() {
    return List.of(
        new CodeSystemImportRequestProperty().setName(DISPLAY).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation),
        new CodeSystemImportRequestProperty().setName(SYNONYM).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation),
        new CodeSystemImportRequestProperty().setName(DISORDER_TYPE).setType(EntityPropertyType.coding).setKind(EntityPropertyKind.property),
        new CodeSystemImportRequestProperty().setName(DISORDER_ID).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property),
        new CodeSystemImportRequestProperty().setName(EXPERT_LINK).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property)
    );
  }

  private static List<Pair<String, String>> getAssociations() {
    return List.of(Pair.of(IS_A, AssociationKind.codesystemHierarchyMeaning), Pair.of(RELATED, AssociationKind.conceptMapEquivalence));
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
    CodeSystemImportRequestConcept concept = mapConcept(node.getDisorder());
    concept.getAssociations().addAll(mapAssociations(parent));
    return concept;
  }

  private static CodeSystemImportRequestConcept mapConcept(OrphanetDisorder disorder) {
    CodeSystemImportRequestConcept concept = new CodeSystemImportRequestConcept();
    concept.setCode(disorder.getOrphaCode());
    concept.setDesignations(mapDesignations(disorder));
    concept.setPropertyValues(mapPropertyValues(disorder));
    concept.setAssociations(mapAssociations(disorder));
    return concept;
  }

  private static List<Designation> mapDesignations(OrphanetDisorder disorder) {
    List<Designation> designations = new ArrayList<>();

    Designation display = new Designation();
    display.setName(disorder.getName().getValue());
    display.setLanguage(disorder.getName().getLang());
    display.setDesignationType(DISPLAY);
    display.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    display.setDesignationKind("text");
    display.setStatus(PublicationStatus.active);
    display.setPreferred(true);
    designations.add(display);

    if (disorder.getSynonymList() != null && disorder.getSynonymList().getSynonyms() != null) {
      disorder.getSynonymList().getSynonyms().forEach(s -> {
        Designation synonym = new Designation();
        synonym.setName(s.getValue());
        synonym.setLanguage(s.getLang());
        synonym.setDesignationType(SYNONYM);
        synonym.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
        synonym.setDesignationKind("text");
        synonym.setStatus(PublicationStatus.active);
        designations.add(synonym);
      });
    }

    if (disorder.getSummaryInformationList() != null && disorder.getSummaryInformationList().getSummaryInformations() != null) {
      disorder.getSummaryInformationList().getSummaryInformations().forEach(si -> {
        if (si.getTextSectionList() != null && si.getTextSectionList().getTextSections() != null) {
          si.getTextSectionList().getTextSections().forEach(ts -> {
            Designation definition = new Designation();
            definition.setName(ts.getContents());
            definition.setLanguage(ts.getLang());
            definition.setDesignationType(DEFINITION);
            definition.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
            definition.setDesignationKind("text");
            definition.setStatus(PublicationStatus.active);
            designations.add(definition);
          });
        }
      });
    }

    return designations;
  }

  private static List<EntityPropertyValue> mapPropertyValues(OrphanetDisorder disorder) {
    List<EntityPropertyValue> values = new ArrayList<>();
    if (disorder.getDisorderType() != null && disorder.getDisorderType().getCategory() != null) {
      EntityPropertyValueCodingValue coding = new EntityPropertyValueCodingValue()
          .setCode(disorder.getDisorderType().getCategory().getValue())
          .setCodeSystem("orpha-disorder-type");
      values.add(new EntityPropertyValue().setValue(coding).setEntityProperty(DISORDER_TYPE));
    }
    if (disorder.getId() != null) {
      values.add(new EntityPropertyValue().setValue(disorder.getId()).setEntityProperty(DISORDER_ID));
    }
    if (disorder.getExpertLink() != null) {
      values.add(new EntityPropertyValue().setValue(disorder.getExpertLink().getValue()).setEntityProperty(EXPERT_LINK));
    }
    return values;
  }

  private static List<CodeSystemAssociation> mapAssociations(OrphanetDisorder disorder) {
    List<CodeSystemAssociation> associations = new ArrayList<>();
    if (disorder.getDisorderDisorderAssociationList() != null && disorder.getDisorderDisorderAssociationList().getDisorderDisorderAssociations() != null) {
      disorder.getDisorderDisorderAssociationList().getDisorderDisorderAssociations().forEach(a -> {
        CodeSystemAssociation association = new CodeSystemAssociation();
        association.setAssociationType(RELATED);
        association.setStatus(PublicationStatus.active);
        association.setTargetCode(a.getRootDisorder().getId());
        associations.add(association);
      });
    }
    return associations;
  }

  private static List<CodeSystemAssociation> mapAssociations(ClassificationNode parent) {
    List<CodeSystemAssociation> associations = new ArrayList<>();
    if (parent != null) {
      CodeSystemAssociation association = new CodeSystemAssociation();
      association.setAssociationType(IS_A);
      association.setStatus(PublicationStatus.active);
      association.setTargetCode(parent.getDisorder().getOrphaCode());
      associations.add(association);
    }
    return associations;
  }
}
