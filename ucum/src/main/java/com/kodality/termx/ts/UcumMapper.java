package com.kodality.termx.ts;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ucum.MeasurementUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class UcumMapper {
  private static final String UCUM = "ucum";

  public Concept toConcept(MeasurementUnit unit) {
    Concept concept = new Concept();
    concept.setCode(unit.getCode());
    concept.setVersions(List.of(toConceptVersion(unit)));
    concept.setCodeSystem(UCUM);
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
