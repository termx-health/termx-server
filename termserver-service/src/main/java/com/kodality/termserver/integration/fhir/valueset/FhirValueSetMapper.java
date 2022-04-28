package com.kodality.termserver.integration.fhir.valueset;


import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class FhirValueSetMapper {

  public static ValueSet mapValueSet(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    ValueSet vs = new ValueSet();
    vs.setId(valueSet.getId());
    vs.setNames(new LocalizedName(Map.of(Language.en, valueSet.getTitle())));
    vs.setDescription(valueSet.getDescription());
    vs.setVersions(List.of(mapVersion(valueSet)));
    vs.setStatus(valueSet.getStatus());
    return vs;
  }

  private static ValueSetVersion mapVersion(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    ValueSetVersion version = new ValueSetVersion();
    version.setValueSet(valueSet.getId());
    version.setVersion(valueSet.getVersion());
    version.setSupportedLanguages(List.of(Language.en));
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(LocalDate.from(valueSet.getDate()));
    return version;
  }
}
