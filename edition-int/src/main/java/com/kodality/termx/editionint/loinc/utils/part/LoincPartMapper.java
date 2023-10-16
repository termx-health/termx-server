package com.kodality.termx.editionint.loinc.utils.part;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.editionint.loinc.utils.LoincImportRequest;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemContent;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestCodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestConcept;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestProperty;
import com.kodality.termx.ts.codesystem.CodeSystemImportRequest.CodeSystemImportRequestVersion;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyKind;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import io.micronaut.core.util.StringUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class LoincPartMapper {
  private static final String DISPLAY = "display";
  private static final String ALIAS = "alias";
  private static final String TYPE = "type";

  public static CodeSystemImportRequest toRequest(LoincImportRequest configuration, List<LoincPart> parts) {
    List<String> langs = new ArrayList<>(List.of(Language.en));
    if (configuration.getLanguage() != null && !configuration.getLanguage().equals(Language.en)) {
      langs.add(configuration.getLanguage());
    }
    CodeSystemImportRequest request = new CodeSystemImportRequest();
    request.setActivate(false);
    request.setCodeSystem(toCodeSystem(langs));
    request.setVersion(toVersion(configuration.getVersion(), langs));

    request.setProperties(getProperties());
    request.setConcepts(toConcepts(parts));
    return request;
  }

  private static CodeSystemImportRequestCodeSystem toCodeSystem(List<String> langs) {
    return new CodeSystemImportRequestCodeSystem().setId("loinc-part")
        .setUri("http://loinc.org/part")
        .setPublisher("Regenstrief Institute, Inc.")
        .setTitle(new LocalizedName(Map.of("en", "LOINC part")))
        .setContent(CodeSystemContent.complete)
        .setCaseSensitive(CaseSignificance.entire_term_case_insensitive)
        .setSupportedLanguages(langs);
  }

  private static CodeSystemImportRequestVersion toVersion(String version, List<String> langs) {
    return new CodeSystemImportRequestVersion()
        .setVersion(version)
        .setSupportedLanguages(langs)
        .setReleaseDate(LocalDate.now());
  }

  private static List<CodeSystemImportRequestProperty> getProperties() {
    return List.of(
        new CodeSystemImportRequestProperty().setName(DISPLAY).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation),
        new CodeSystemImportRequestProperty().setName(ALIAS).setType(EntityPropertyType.string).setKind(EntityPropertyKind.designation),
        new CodeSystemImportRequestProperty().setName(TYPE).setType(EntityPropertyType.string).setKind(EntityPropertyKind.property));
  }

  private static List<CodeSystemImportRequestConcept> toConcepts(List<LoincPart> parts) {
    return parts.stream().map(LoincPartMapper::mapConcept).toList();
  }

  private static CodeSystemImportRequestConcept mapConcept(LoincPart part) {
    CodeSystemImportRequestConcept concept = new CodeSystemImportRequestConcept();
    concept.setCode(part.getCode());
    concept.setDesignations(mapDesignations(part));
    concept.setPropertyValues(mapPropertyValues(part));
    return concept;
  }

  private static List<Designation> mapDesignations(LoincPart part) {
    List<Designation> designations = new ArrayList<>();
    if (part.getDisplay() != null) {
      designations.addAll(part.getDisplay().entrySet().stream().map(e -> mapDesignation(e.getValue(), DISPLAY, e.getKey())).toList());
    }
    if (part.getAlias() != null) {
      designations.add(mapDesignation(part.getAlias(), ALIAS, Language.en));
    }
    return designations;
  }

  private static Designation mapDesignation(String name, String type, String lang) {
    return new Designation()
        .setName(name)
        .setLanguage(lang)
        .setCaseSignificance(CaseSignificance.entire_term_case_insensitive)
        .setDesignationKind("text")
        .setStatus(PublicationStatus.active)
        .setDesignationType(type)
        .setPreferred(type.equals(DISPLAY));
  }

  private static List<EntityPropertyValue> mapPropertyValues(LoincPart part) {
    EntityPropertyValue type = new EntityPropertyValue().setValue(part.getType()).setEntityProperty(TYPE);
    return Stream.of(type).filter(v -> StringUtils.isNotEmpty((String) v.getValue())).toList();
  }
}
