package com.kodality.termserver.ts;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.observationdefintion.ObservationDefinition;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.Designation;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class ObservationDefinitionMapper {
  private static final String OBS_DEF = "observation-definition";

  public Concept toConcept(ObservationDefinition def) {
    Concept concept = new Concept();
    concept.setCode(def.getCode());
    concept.setVersions(List.of(toConceptVersion(def)));
    concept.setCodeSystem(OBS_DEF);
    return concept;
  }

  private CodeSystemEntityVersion toConceptVersion(ObservationDefinition def) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(def.getCode());
    version.setCodeSystem(OBS_DEF);
    version.setStatus(PublicationStatus.draft);

    List<Designation> designations = new ArrayList<>();
    designations.addAll(toConceptDesignations(def.getNames(), "display"));
    designations.addAll(toConceptDesignations(def.getAlias(), "alias"));
    designations.addAll(toConceptDesignations(def.getDefinition(), "definition"));
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
