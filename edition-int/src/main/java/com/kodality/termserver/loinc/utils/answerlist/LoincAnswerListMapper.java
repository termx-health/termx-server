package com.kodality.termserver.loinc.utils.answerlist;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.loinc.utils.LoincImportRequest;
import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemContent;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestCodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestConcept;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestProperty;
import com.kodality.termserver.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestVersion;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityPropertyKind;
import com.kodality.termserver.ts.codesystem.EntityPropertyType;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
import com.kodality.termserver.ts.mapset.MapSet;
import com.kodality.termserver.ts.mapset.MapSetAssociation;
import com.kodality.termserver.ts.mapset.MapSetEntityVersion;
import com.kodality.termserver.ts.mapset.MapSetTransactionRequest;
import com.kodality.termserver.ts.mapset.MapSetVersion;
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
        .setPublisher("Regenstrief Institute, Inc.")
        .setTitle(new LocalizedName(Map.of("en", "LOINC answer list")))
        .setContent(CodeSystemContent.complete)
        .setCaseSensitive(CaseSignificance.entire_term_case_insensitive)
        .setSupportedLanguages(List.of(Language.en));
  }

  private static CodeSystemImportRequestVersion toVersion(String version) {
    return new CodeSystemImportRequestVersion()
        .setVersion(version)
        .setSupportedLanguages(List.of(Language.en))
        .setReleaseDate(LocalDate.now());
  }

  private static List<CodeSystemImportRequestProperty> getProperties() {
    return List.of(
        new CodeSystemImportRequestProperty().setName(DISPLAY).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation),
        new CodeSystemImportRequestProperty().setName(ANSWER_CODE).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property),
        new CodeSystemImportRequestProperty().setName(TYPE).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property),
        new CodeSystemImportRequestProperty().setName(OID).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property));
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

  public static MapSetTransactionRequest toRequest(LoincImportRequest request, List<Pair<String, String>> mappings) {
    MapSet mapSet = new MapSet();
    mapSet.setId("loinc-answer-to-snomed");
    mapSet.setUri("loinc-answer-to-snomed");
    mapSet.setNames(new LocalizedName(Map.of("en", "LOINC answer to SNOMED CT")));
    mapSet.setSourceCodeSystems(List.of("loinc-answer-list"));
    mapSet.setTargetCodeSystems(List.of("snomed-ct"));

    MapSetVersion version = new MapSetVersion();
    version.setVersion(request.getVersion());
    version.setMapSet("loinc-answer-to-snomed");
    version.setSource("Regenstrief Institute, Inc.");
    version.setSupportedLanguages(List.of(Language.en));
    version.setStatus(PublicationStatus.draft);

    List<MapSetAssociation> associations = mappings.stream().map(p -> {
      MapSetAssociation association = new MapSetAssociation()
          .setSource(new CodeSystemEntityVersion().setCode(p.getKey()).setCodeSystem("loinc-answer-list"))
          .setTarget(new CodeSystemEntityVersion().setCode(p.getValue()).setCodeSystem("snomed-ct"))
          .setAssociationType(IS_A)
          .setStatus(PublicationStatus.active);
      association.setVersions(List.of(new MapSetEntityVersion().setMapSet("loinc-answer-to-snomed").setStatus(PublicationStatus.draft)));
      return association;
    }).toList();
    return new MapSetTransactionRequest().setMapSet(mapSet).setVersion(version).setAssociations(associations);
  }
}
