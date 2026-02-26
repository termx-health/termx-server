package com.kodality.termx.ucum.ts;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
import com.kodality.termx.ucum.MeasurementUnit;
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

  public Concept toConcept(MeasurementUnit unit) {
    Concept concept = new Concept();
    concept.setCode(unit.getCode());
    concept.setVersions(List.of(toConceptVersion(unit)));
    concept.setCodeSystem(UCUM);
    return concept;
  }

  public ValueSetVersionConceptValue toVSConcept(MeasurementUnit unit) {
    ValueSetVersionConceptValue concept = new ValueSetVersionConceptValue();
    concept.setCode(unit.getCode());
    concept.setCodeSystem(UCUM);
    concept.setCodeSystemUri(UCUM_URI);
    return concept;
  }

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

  public CodeSystemEntityVersion toConceptVersion(MeasurementUnit unit) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(unit.getCode());
    version.setCodeSystem(UCUM);
    version.setStatus(PublicationStatus.draft);

    List<Designation> designations = new ArrayList<>();
    designations.addAll(toConceptDesignations(unit.getAlias(), "alias"));
    designations.addAll(toConceptDesignations(unit.getNames(), "display"));
    version.setDesignations(designations);

    return version;
  }

  public CodeSystemEntityVersion toConceptVersion(UcumUnitDefinition unit) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(unit.getCode());
    version.setCodeSystem(UCUM);
    version.setStatus(PublicationStatus.draft);

    List<Designation> designations = Optional.ofNullable(unit.getNames()).orElse(List.of()).stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(n -> !n.isEmpty())
        .distinct()
        .map(this::toDisplayDesignation)
        .collect(Collectors.toCollection(ArrayList::new));
    if (designations.isEmpty()) {
      designations.add(toDisplayDesignation(unit.getCode()));
    }
    designations.get(0).setPreferred(true);
    version.setDesignations(designations);
    return version;
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

  private List<Designation> toConceptDesignations(LocalizedName names, String type) {
    if (names == null) {
      return new ArrayList<>();
    }
    return names.keySet().stream().map(lang -> {
      Designation designation = new Designation();
      designation.setName(names.get(lang));
      designation.setLanguage(lang);
      designation.setDesignationType(type);
      designation.setCaseSignificance(CaseSignificance.entire_term_case_insensitive);
      designation.setStatus(PublicationStatus.active);
      return designation;
    }).collect(Collectors.toList());
  }

}
