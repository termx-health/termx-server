package com.kodality.termserver.atc.utils;

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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class AtcMapper {
  private static final String DISPLAY = "display";
  private static final String IS_A = "is-a";

  public static CodeSystemImportRequest toRequest(CodeSystemImportConfiguration configuration, Map<String, String> atc) {
    List<String> supportedLanguages = List.of(Language.en);

    CodeSystemImportRequest request = new CodeSystemImportRequest(configuration);
    request.getCodeSystem().setContent(CodeSystemContent.complete).setSupportedLanguages(supportedLanguages);
    request.getVersion().setSupportedLanguages(supportedLanguages);

    request.setProperties(getProperties()).setAssociations(getAssociations());
    request.setConcepts(toConcepts(atc));
    return request;
  }

  private static List<Pair<String, String>> getProperties() {
    return List.of(Pair.of(DISPLAY, EntityPropertyType.string));
  }

  private static List<Pair<String, String>> getAssociations() {
    return List.of(Pair.of(IS_A, AssociationKind.codesystemHierarchyMeaning));
  }

  private static List<CodeSystemImportRequestConcept> toConcepts(Map<String, String> atc) {
    return atc.entrySet().stream().map(a -> {
      CodeSystemImportRequestConcept concept = new CodeSystemImportRequestConcept();
      concept.setCode(a.getKey());
      concept.setDesignations(mapDesignations(a));
      concept.setAssociations(mapAssociations(a, atc));
      return concept;

    }).collect(Collectors.toList());
  }

  private static List<Designation> mapDesignations(Entry<String, String> atc) {
    Designation designation = new Designation();
    designation.setName(atc.getValue());
    designation.setLanguage(Language.en);
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setDesignationKind("text");
    designation.setStatus(PublicationStatus.active);
    designation.setDesignationType(DISPLAY);
    designation.setPreferred(true);
    return List.of(designation);
  }

  private static List<CodeSystemAssociation> mapAssociations(Entry<String, String> a, Map<String, String> atc) {
    String targetCode = findParent(a.getKey(), atc, 1);
    if (targetCode == null) {
      return List.of();
    }
    CodeSystemAssociation association = new CodeSystemAssociation();
    association.setAssociationType(IS_A);
    association.setStatus(PublicationStatus.active);
    association.setTargetCode(targetCode);
    return List.of(association);
  }

  private static String findParent(String child, Map<String, String> atc, int offset) {
    if (child.length() < 2) {
      return null;
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
