package com.kodality.termserver.fhir.conceptmap;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.fhir.codesystem.CodeSystemFhirService;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetAssociationQueryParams;
import com.kodality.termserver.mapset.MapSetEntityVersion;
import com.kodality.termserver.mapset.MapSetQueryParams;
import com.kodality.termserver.mapset.MapSetVersion;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.mapset.MapSetService;
import com.kodality.termserver.ts.mapset.MapSetVersionService;
import com.kodality.termserver.ts.mapset.association.MapSetAssociationService;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.OperationOutcome.OperationOutcomeIssue;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ConceptMapFhirService {
  private final ConceptMapFhirMapper mapper;
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final CodeSystemFhirService codeSystemFhirService;
  private final MapSetAssociationService mapSetAssociationService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  public Parameters translate(Map<String, List<String>> params) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    if (fhirParams.getFirst("code").isEmpty() || fhirParams.getFirst("system").isEmpty()) {
      return new Parameters();
    }

    MapSetQueryParams msParams = new MapSetQueryParams()
        .setUri(fhirParams.getFirst("uri").orElse(null))
        .setVersionVersion(fhirParams.getFirst("conceptMapVersion").orElse(null))
        .setAssociationSourceCode(fhirParams.getFirst("code").orElse(null))
        .setAssociationSourceSystemUri(fhirParams.getFirst("system").orElse(null))
        .setAssociationSourceSystemVersion(fhirParams.getFirst("version").orElse(null))
        .setAssociationTargetSystem(fhirParams.getFirst("targetSystem").orElse(null))
        .setAssociationsDecorated(true);
    Optional<MapSet> mapSet = mapSetService.query(msParams).findFirst();
    return mapper.toFhirParameters(mapSet.orElse(null));
  }

  @Transactional
  public ConceptMap closure(Parameters params, OperationOutcome outcome) {
    outcome.setIssue(new ArrayList<>());
    Optional<String> name = params.getParameter().stream().filter(p -> p.getName().equals("name")).map(ParametersParameter::getValueString).findFirst();
    Optional<String> version = params.getParameter().stream().filter(p -> p.getName().equals("version")).map(ParametersParameter::getValueString).findFirst();
    List<Coding> concepts = params.getParameter().stream().filter(p -> p.getName().equals("concept")).map(ParametersParameter::getValueCoding).toList();

    if (name.isEmpty()) {
      outcome.getIssue().add(new OperationOutcomeIssue().setSeverity("error").setCode("required")
          .setDetails(new CodeableConcept().setText("Name not provided")));
      return null;
    }

    if (CollectionUtils.isNotEmpty(concepts)) {
      Optional<MapSet> persistedMapSet = mapSetService.load(name.get());
      Optional<MapSetVersion> persistedMapSetVersion = version.isPresent() ? mapSetVersionService.load(name.get(), version.get()) : Optional.empty();
      if (persistedMapSet.isPresent()) {
        MapSetVersion mapSetVersion = persistedMapSetVersion.orElse(null);
        if (mapSetVersion == null) {
          mapSetVersion = new MapSetVersion().setMapSet(persistedMapSet.get().getId()).setReleaseDate(LocalDate.now()).setStatus(PublicationStatus.draft).setDescription(String.format("Updates for Closure Table %s", name.get()));
          if (version.isPresent()) {
            mapSetVersion.setVersion(version.get());
          } else {
            MapSetVersion lastVersion = mapSetVersionService.loadLastVersion(persistedMapSet.get().getId(), PublicationStatus.active);
            try {
              mapSetVersion.setVersion(String.valueOf(Long.parseLong(lastVersion.getVersion()) + 1));
            } catch (Exception e) {
              mapSetVersion.setVersion(lastVersion.getVersion() + ".1");
            }
          }
          mapSetVersionService.save(mapSetVersion);
        }

        List<MapSetAssociation> associations = composeAssociations(mapSetVersion.getId(), concepts);
        associations.forEach(association -> mapSetAssociationService.save(association, persistedMapSet.get().getId()));
        mapSetVersionService.saveEntityVersions(mapSetVersion.getId(), associations.stream().map(c -> c.getVersions().get(0)).collect(Collectors.toList()));
        mapSetVersionService.activate(name.get(), mapSetVersion.getVersion());
        mapSetVersion.setAssociations(associations);
        return mapper.toFhir(persistedMapSet.get(), mapSetVersion);
      }
      outcome.getIssue().add(new OperationOutcomeIssue().setSeverity("error").setCode("generated")
          .setDetails(new CodeableConcept().setText("Invalid closure name, concept map does not exist")));
      return null;
    }

    if (version.isPresent()) {
      Optional<MapSet> mapSet = mapSetService.load(name.get());
      Optional<MapSetVersion> mapSetVersion = mapSetVersionService.load(name.get(), version.get());
      if (mapSet.isPresent() && mapSetVersion.isPresent()) {
        MapSetAssociationQueryParams ap = new MapSetAssociationQueryParams().setMapSetVersionId(mapSetVersion.get().getId());
        ap.all();
        mapSetVersion.get().setAssociations(mapSetAssociationService.query(ap).getData());
        return mapper.toFhir(mapSet.get(), mapSetVersion.get());
      }
      outcome.getIssue().add(new OperationOutcomeIssue().setSeverity("error").setCode("not-found")
          .setDetails(new CodeableConcept().setText("Concept Map version not found")));
      return null;
    }


    Optional<MapSet> persistedMapSet = mapSetService.load(name.get());
    if (persistedMapSet.isEmpty()) {
      MapSet mapSet = new MapSet().setId(name.get()).setNames(new LocalizedName(Map.of(Language.en, String.format("Closure Table %s", name.get()))));
      mapSetService.save(mapSet);
      MapSetVersion msVersion = new MapSetVersion().setMapSet(mapSet.getId()).setVersion("0").setReleaseDate(LocalDate.now()).setStatus(PublicationStatus.draft)
          .setDescription(String.format("Closure Table %s Creation", name.get()));
      mapSetVersionService.save(msVersion);
      mapSetVersionService.activate(msVersion.getMapSet(), msVersion.getVersion());
      return mapper.toFhir(mapSet, msVersion);
    }
    outcome.getIssue().add(new OperationOutcomeIssue().setSeverity("error").setCode("generated")
        .setDetails(new CodeableConcept().setText("Closure table is already initialized")));
    return null;


  }

  private List<MapSetAssociation> composeAssociations(Long versionId, List<Coding> concepts) {
    List<MapSetAssociation> associations = new ArrayList<>();

    MapSetAssociationQueryParams params = new MapSetAssociationQueryParams().setMapSetVersionId(versionId);
    params.all();
    List<MapSetAssociation> persistedAssociations = mapSetAssociationService.query(params).getData();

    concepts.forEach(source -> concepts.forEach(target -> {
      if (source.getCode().equals(target.getCode()) && source.getSystem().equals(target.getSystem())) {
        return;
      }
      OperationOutcome outcome = new OperationOutcome();
      Parameters res = codeSystemFhirService.subsumes(
          new Parameters().setParameter(List.of(
              new ParametersParameter().setName("codingA").setValueCoding(source),
              new ParametersParameter().setName("codingB").setValueCoding(target))),
          outcome);
      if (res != null) {
        MapSetAssociation a = new MapSetAssociation();
        a.setSource(findEntityVersion(source));
        a.setTarget(findEntityVersion(target));
        a.setAssociationType(res.getParameter().stream().filter(p -> p.getName().equals("outcome")).findFirst().map(ParametersParameter::getValueCode).orElse(null));
        a.setStatus(PublicationStatus.active);
        a.setVersions(new ArrayList<>(List.of(new MapSetEntityVersion().setStatus(PublicationStatus.active))));
        associations.add(a);
      }
    }));

    associations.addAll(persistedAssociations);
    return associations;
  }

  private CodeSystemEntityVersion findEntityVersion(Coding coding) {
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setCode(coding.getCode());
    params.setCodeSystemUri(coding.getSystem());
    params.setCodeSystemVersion(coding.getVersion());
    params.setLimit(1);
    return codeSystemEntityVersionService.query(params).findFirst().orElse(null);
  }

  public OperationOutcome error(Map<String, List<String>> params) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    OperationOutcomeIssue issue = new OperationOutcomeIssue().setSeverity("error");
    if (fhirParams.getFirst("code").isEmpty()) {
      issue.setCode("required");
      issue.setDetails(new CodeableConcept().setText("No code parameter provided in request"));
    } else if (fhirParams.getFirst("system").isEmpty()) {
      issue.setCode("required");
      issue.setDetails(new CodeableConcept().setText("No system parameter provided in request"));
    } else {
      issue.setCode("not-found");
      issue.setDetails(new CodeableConcept().setText("Translation for code '" + fhirParams.getFirst("code").get() + "' not found"));
    }
    return new OperationOutcome(issue);
  }
}
