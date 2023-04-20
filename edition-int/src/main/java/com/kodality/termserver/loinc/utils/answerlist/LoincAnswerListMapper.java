package com.kodality.termserver.loinc.utils.answerlist;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.loinc.utils.LoincImportRequest;
import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemContent;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestCodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestConcept;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestVersion;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityPropertyType;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import io.micronaut.core.util.StringUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

public class LoincAnswerListMapper {
  private static final String IS_A = "is-a";
  private static final String DISPLAY = "display";
  private static final String TYPE = "type";
  private static final String OID = "oid";
  private static final String ANSWER_CODE = "answer-code";

  public static CodeSystemImportRequest toRequest(LoincImportRequest configuration, List<LoincAnswerList> answers, List<LoincAnswerList> answerLists) {
    CodeSystemImportRequest request = new CodeSystemImportRequest();
    request.setActivate(false);
    request.setCodeSystem(toCodeSystem());
    request.setVersion(toVersion(configuration.getVersion()));

    request.setProperties(getProperties());
    request.setConcepts(new ArrayList<>());
    request.getConcepts().addAll(toConcepts(answers, "answer"));
    request.getConcepts().addAll(toConcepts(answerLists, "answer-list"));
    return request;
  }

  private static CodeSystemImportRequestCodeSystem toCodeSystem() {
    return new CodeSystemImportRequestCodeSystem().setId("loinc-answer-list")
        .setUri("http://loinc.org/answer-list")
        .setNames(new LocalizedName(Map.of("en", "LOINC answer list")))
        .setContent(CodeSystemContent.complete)
        .setCaseSensitive(CaseSignificance.entire_term_case_insensitive)
        .setSupportedLanguages(List.of(Language.en));
  }

  private static CodeSystemImportRequestVersion toVersion(String version) {
    return new CodeSystemImportRequestVersion()
        .setVersion(version)
        .setSource("Regenstrief Institute, Inc.")
        .setSupportedLanguages(List.of(Language.en))
        .setReleaseDate(LocalDate.now());
  }

  private static List<Pair<String, String>> getProperties() {
    return List.of(
        Pair.of(DISPLAY, EntityPropertyType.string),
        Pair.of(ANSWER_CODE, EntityPropertyType.string),
        Pair.of(TYPE, EntityPropertyType.string),
        Pair.of(OID, EntityPropertyType.string));
  }

  private static List<CodeSystemImportRequestConcept> toConcepts(List<LoincAnswerList> answerLists, String type) {
    return answerLists.stream().map(c -> mapConcept(c, type)).collect(Collectors.toList());
  }

  private static CodeSystemImportRequestConcept mapConcept(LoincAnswerList answerList, String type) {
    CodeSystemImportRequestConcept concept = new CodeSystemImportRequestConcept();
    concept.setCode(answerList.getCode());
    concept.setDesignations(mapDesignations(answerList));
    concept.setPropertyValues(mapPropertyValues(answerList, type));
    concept.setAssociations(mapAssociations(answerList));
    return concept;
  }

  private static List<Designation> mapDesignations(LoincAnswerList answerList) {
    if (answerList.getDisplay() == null) {
      return List.of();
    }
    Designation designation = new Designation()
        .setName(answerList.getDisplay())
        .setLanguage(Language.en)
        .setCaseSignificance(CaseSignificance.entire_term_case_insensitive)
        .setDesignationKind("text")
        .setStatus(PublicationStatus.active)
        .setDesignationType(DISPLAY)
        .setPreferred(true);
    return List.of(designation);
  }

  private static List<EntityPropertyValue> mapPropertyValues(LoincAnswerList answerList, String t) {
    EntityPropertyValue answerCode = new EntityPropertyValue().setValue(answerList.getAnswerCode()).setEntityProperty(ANSWER_CODE);
    EntityPropertyValue oid = new EntityPropertyValue().setValue(answerList.getOid()).setEntityProperty(OID);
    EntityPropertyValue type = new EntityPropertyValue().setValue(t).setEntityProperty(TYPE);
    return Stream.of(answerCode, oid, type).filter(v -> StringUtils.isNotEmpty((String) v.getValue())).toList();
  }

  private static List<CodeSystemAssociation> mapAssociations(LoincAnswerList answerList) {
    if (answerList.getAnswerLists() == null) {
      return List.of();
    }
    return answerList.getAnswerLists().stream().map(a -> new CodeSystemAssociation()
        .setAssociationType(IS_A)
        .setStatus(PublicationStatus.active)
        .setOrderNumber(a.getValue())
        .setTargetCode(a.getKey())).toList();
  }
}
