package com.kodality.termx.fhir.conceptmap;

import com.kodality.termx.fhir.BaseFhirMapper;
import com.kodality.termx.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.valueset.ValueSetService;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.datatypes.ContactPoint;
import com.kodality.zmei.fhir.datatypes.Narrative;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroup;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElement;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElementTarget;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ConceptMapFhirMapper extends BaseFhirMapper {
  private final CodeSystemService codeSystemService;
  private final ValueSetService valueSetService;

  public static String toFhirId(MapSet mapSet, MapSetVersion version) {
    return mapSet.getId() + "@" + version.getVersion();
  }

  public String toFhirJson(MapSet mapSet, MapSetVersion version) {
    return FhirMapper.toJson(toFhir(mapSet, version));
  }

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
    fhirConceptMap.setSourceScopeUri(Optional.ofNullable(mapSet.getSourceValueSet()).map(vs -> valueSetService.load(vs).getUri()).orElse(null));
    fhirConceptMap.setTargetScopeUri(Optional.ofNullable(mapSet.getTargetValueSet()).map(vs -> valueSetService.load(vs).getUri()).orElse(null));

    return fhirConceptMap;
  }

  private List<ConceptMapGroup> toFhirGroup(List<MapSetAssociation> associations) {
    if (associations == null) {
      return new ArrayList<>();
    }
    Map<String, List<MapSetAssociation>> grouped = associations.stream().collect(Collectors.groupingBy(a -> a.getSource().getCodeSystem() + a.getTarget().getCodeSystem()));
    return grouped.values().stream().map(elements -> {
      ConceptMapGroup group = new ConceptMapGroup();
      group.setSource(codeSystemService.load(elements.get(0).getSource().getCodeSystem()).map(CodeSystem::getUri).orElse(null));
      group.setTarget(codeSystemService.load(elements.get(0).getTarget().getCodeSystem()).map(CodeSystem::getUri).orElse(null));
      group.setElement(elements.stream().map(el -> new ConceptMapGroupElement()
          .setCode(el.getSource().getCode())
          .setTarget(List.of(new ConceptMapGroupElementTarget()
              .setCode(el.getTarget().getCode())
              .setRelationship(el.getAssociationType()))))
          .collect(Collectors.toList()));
      return group;
    }).collect(Collectors.toList());
  }



}
