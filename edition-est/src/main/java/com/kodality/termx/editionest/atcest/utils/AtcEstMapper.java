package com.kodality.termx.editionest.atcest.utils;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class AtcEstMapper {
  private static final String DISPLAY = "display";
  private static final String IS_A = "is-a";

  public static CodeSystemImportRequest toRequest(CodeSystemImportConfiguration configuration, List<AtcEst> atc) {
    List<String> supportedLanguages = List.of(Language.en, Language.et);

    CodeSystemImportRequest request = new CodeSystemImportRequest(configuration);
    request.getCodeSystem().setContent(CodeSystemContent.supplement).setSupportedLanguages(supportedLanguages).setHierarchyMeaning(IS_A);
    request.getVersion().setSupportedLanguages(supportedLanguages);

    request.setProperties(getProperties()).setAssociations(getAssociations());
    request.setConcepts(toConcepts(atc));
    return request;
  }

  private static List<CodeSystemImportRequestProperty> getProperties() {
    return List.of(new CodeSystemImportRequestProperty().setName(DISPLAY).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation));
  }

  private static List<Pair<String, String>> getAssociations() {
    return List.of(Pair.of(IS_A, AssociationKind.codesystemHierarchyMeaning));
  }

  private static List<CodeSystemImportRequestConcept> toConcepts(List<AtcEst> atc) {
    return atc.stream().filter(a -> a.getCode() != null).map(a -> {
      CodeSystemImportRequestConcept concept = new CodeSystemImportRequestConcept();
      concept.setCode(a.getCode());
      concept.setDesignations(mapDesignations(a));
      concept.setAssociations(mapAssociations(a, atc));
      return concept;
    }).collect(Collectors.toList());
  }

  private static List<Designation> mapDesignations(AtcEst atc) {
    Designation designation = new Designation();
    designation.setName(atc.getName());
    designation.setLanguage(Language.et);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setDesignationType(DISPLAY);
    designation.setPreferred(true);
    return new ArrayList<>(List.of(designation));
  }

  private static List<CodeSystemAssociation> mapAssociations(AtcEst a, List<AtcEst> atc) {
    String targetCode = findParent(a.getCode(), atc, 1);
    if (targetCode == null) {
      return new ArrayList<>();
    }
    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setAssociationType(IS_A);
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(targetCode);
    return new ArrayList<>(List.of(association));
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
