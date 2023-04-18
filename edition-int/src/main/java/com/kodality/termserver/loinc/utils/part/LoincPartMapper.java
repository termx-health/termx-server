package com.kodality.termserver.loinc.utils.part;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.loinc.utils.LoincImportRequest;
import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.PublicationStatus;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

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
        .setNames(new LocalizedName(Map.of("en", "LOINC part")))
        .setContent(CodeSystemContent.complete)
        .setCaseSensitive(CaseSignificance.entire_term_case_insensitive)
        .setSupportedLanguages(langs);
  }

  private static CodeSystemImportRequestVersion toVersion(String version, List<String> langs) {
    return new CodeSystemImportRequestVersion()
        .setVersion(version)
        .setSource("Regenstrief Institute, Inc.")
        .setSupportedLanguages(langs)
        .setReleaseDate(LocalDate.now());
  }

  private static List<Pair<String, String>> getProperties() {
    return List.of(
        Pair.of(DISPLAY, EntityPropertyType.string),
        Pair.of(ALIAS, EntityPropertyType.string),
        Pair.of(TYPE, EntityPropertyType.string));
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
