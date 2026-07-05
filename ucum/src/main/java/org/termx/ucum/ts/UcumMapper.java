package org.termx.ucum.ts;

import org.termx.ts.CaseSignificance;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;

@Singleton
public class UcumMapper {
  private static final String UCUM = "ucum";
  private static final String UCUM_URI = "http://unitsofmeasure.org";

  public Concept toConcept(UcumUnitDefinition unit) {
    Concept concept = new Concept();
    concept.setCode(unit.getCode());
    concept.setVersions(List.of(toConceptVersion(unit)));
    concept.setCodeSystem(UCUM);
    return concept;
  }

  public Concept toExpressionConcept(String code) {
    Concept concept = new Concept();
    concept.setCode(code);
    concept.setVersions(List.of(toExpressionConceptVersion(code)));
    concept.setCodeSystem(UCUM);
    return concept;
  }

  public ValueSetVersionConceptValue toVSConcept(UcumUnitDefinition unit) {
    ValueSetVersionConceptValue concept = new ValueSetVersionConceptValue();
    concept.setCode(unit.getCode());
    concept.setCodeSystem(UCUM);
    concept.setCodeSystemUri(UCUM_URI);
    return concept;
  }

  public CodeSystemEntityVersion toConceptVersion(UcumUnitDefinition unit) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(unit.getCode());
    version.setCodeSystem(UCUM);
    version.setStatus(PublicationStatus.draft);

    List<Designation> designations = new ArrayList<>();
    // Base UCUM (English) display names, from the essence file.
    Optional.ofNullable(unit.getNames()).orElse(List.of()).stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(n -> !n.isEmpty())
        .distinct()
        .map(this::toDisplayDesignation)
        .forEach(designations::add);
    // Supplement designations, keeping their real language + type (et/ru displays, aliases) instead of
    // defaulting to English — otherwise localized values are mislabelled `en`.
    Optional.ofNullable(unit.getSupplementDesignations()).orElse(List.of()).stream()
        .filter(d -> d != null && d.getName() != null && !d.getName().isBlank())
        .map(this::toSupplementDesignation)
        .forEach(designations::add);
    designations = dedupeDesignations(designations);
    if (designations.isEmpty()) {
      designations.add(toDisplayDesignation(unit.getCode()));
    }
    designations.get(0).setPreferred(true);
    version.setDesignations(designations);
    return version;
  }

  private Designation toSupplementDesignation(Designation source) {
    Designation designation = new Designation();
    designation.setName(source.getName());
    designation.setLanguage(source.getLanguage());
    designation.setDesignationType(source.getDesignationType() == null || source.getDesignationType().isBlank()
        ? "display" : source.getDesignationType());
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setStatus(PublicationStatus.active);
    return designation;
  }

  /** De-dupe by (language, type, name) so a supplement designation that repeats a base name is not emitted twice. */
  private static List<Designation> dedupeDesignations(List<Designation> designations) {
    java.util.LinkedHashMap<String, Designation> byKey = new java.util.LinkedHashMap<>();
    for (Designation d : designations) {
      String key = (d.getLanguage() == null ? "" : d.getLanguage()) + "|"
          + (d.getDesignationType() == null ? "" : d.getDesignationType()) + "|"
          + (d.getName() == null ? "" : d.getName());
      byKey.putIfAbsent(key, d);
    }
    return new ArrayList<>(byKey.values());
  }

  private CodeSystemEntityVersion toExpressionConceptVersion(String code) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(code);
    version.setCodeSystem(UCUM);
    version.setStatus(PublicationStatus.draft);
    version.setDesignations(List.of(toDisplayDesignation(code).setPreferred(true)));
    return version;
  }

  private Designation toDisplayDesignation(String value) {
    Designation designation = new Designation();
    designation.setName(value);
    designation.setLanguage("en");
    designation.setDesignationType("display");
    designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
    designation.setStatus(PublicationStatus.active);
    return designation;
  }

}
