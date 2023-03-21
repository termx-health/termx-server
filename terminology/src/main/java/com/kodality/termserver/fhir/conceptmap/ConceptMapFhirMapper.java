package com.kodality.termserver.fhir.conceptmap;

import com.kodality.termserver.terminology.codesystem.CodeSystemService;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.ts.mapset.MapSet;
import com.kodality.termserver.ts.mapset.MapSetAssociation;
import com.kodality.termserver.ts.mapset.MapSetVersion;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.datatypes.ContactPoint;
import com.kodality.zmei.fhir.datatypes.Narrative;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroup;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElement;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElementTarget;
import io.micronaut.core.util.CollectionUtils;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ConceptMapFhirMapper {
  private final CodeSystemService codeSystemService;

  public com.kodality.zmei.fhir.resource.terminology.ConceptMap toFhir(MapSet mapSet, MapSetVersion version) {
    com.kodality.zmei.fhir.resource.terminology.ConceptMap fhirConceptMap = new com.kodality.zmei.fhir.resource.terminology.ConceptMap();
    fhirConceptMap.setId(mapSet.getId());
    fhirConceptMap.setUrl(mapSet.getUri());
    fhirConceptMap.setName(mapSet.getNames().getOrDefault(Language.en, mapSet.getNames().values().stream().findFirst().orElse(null)));
    fhirConceptMap.setContact(mapSet.getContacts() == null ? null : mapSet.getContacts().stream()
        .map(c -> new ContactDetail().setName(c.getName()).setTelecom(c.getTelecoms() == null ? null : c.getTelecoms().stream().map(t ->
            new ContactPoint().setSystem(t.getSystem()).setValue(t.getValue()).setUse(t.getUse())).collect(Collectors.toList())))
        .collect(Collectors.toList()));
    fhirConceptMap.setText(mapSet.getNarrative() == null ? null : new Narrative().setDiv(mapSet.getNarrative()));
    fhirConceptMap.setDescription(mapSet.getDescription());
    fhirConceptMap.setVersion(version.getVersion());
    fhirConceptMap.setDate(OffsetDateTime.of(version.getReleaseDate().atTime(0, 0), ZoneOffset.UTC));
    fhirConceptMap.setStatus(version.getStatus());
    fhirConceptMap.setPublisher(version.getSource());
    fhirConceptMap.setGroup(toFhirGroup(version.getAssociations()));
    fhirConceptMap.setSourceCanonical(mapSet.getSourceValueSet());
    fhirConceptMap.setTargetCanonical(mapSet.getTargetValueSet());

    return fhirConceptMap;
  }

  private List<ConceptMapGroup> toFhirGroup(List<MapSetAssociation> associations) {
    if (associations == null) {
      return new ArrayList<>();
    }
    return associations.stream().map(association -> {
      ConceptMapGroup group = new ConceptMapGroup();
      group.setSource(association.getSource().getCodeSystem());
      group.setTarget(association.getTarget().getCodeSystem());
      group.setElement(new ArrayList<>(List.of(
          new ConceptMapGroupElement().setCode(association.getSource().getCode()).setTarget(new ArrayList<>(List.of(
              new ConceptMapGroupElementTarget().setCode(association.getTarget().getCode()).setEquivalence(association.getAssociationType())
          )))
      )));
      return group;
    }).collect(Collectors.toList());
  }


  public Parameters toFhirParameters(MapSet ms) {
    Parameters parameters = new Parameters();
    if (ms != null) {
      List<ParametersParameter> parameter = new ArrayList<>();
      List<ParametersParameter> matches = extractMatches(ms);
      parameter.add(new ParametersParameter().setName("result").setValueBoolean(CollectionUtils.isNotEmpty(matches)));
      parameter.addAll(matches);
      parameters.setParameter(parameter);
    } else {
      List<ParametersParameter> parameter = new ArrayList<>();
      parameter.add(new ParametersParameter().setName("result").setValueBoolean(false));
      parameters.setParameter(parameter);
    }
    return parameters;
  }

  private List<ParametersParameter> extractMatches(MapSet ms) {
    if (ms.getAssociations() == null) {
      return new ArrayList<>();
    }
    return ms.getAssociations().stream().map(association -> {
      List<ParametersParameter> parts = new ArrayList<>();
      String csUri = codeSystemService.query(new CodeSystemQueryParams().setCodeSystemEntityVersionId(association.getTarget().getId())).findFirst().map(CodeSystem::getUri).orElse(null);
      parts.add(new ParametersParameter().setName("equivalence").setValueCode(association.getAssociationType()));
      parts.add(new ParametersParameter().setName("concept").setValueCoding(new Coding().setCode(association.getTarget().getCode()).setSystem(csUri)));
      return new ParametersParameter().setName("match").setPart(parts);
    }).collect(Collectors.toList());
  }
}
