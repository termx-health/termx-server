package com.kodality.termserver.fhir.codesystem;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.termserver.ts.codesystem.CodeSystemService;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionService;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.OperationOutcome.OperationOutcomeIssue;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemFhirService {
  private final CodeSystemFhirMapper mapper;
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final CodeSystemFhirImportService fhirImportService;

  public Parameters lookup(Map<String, List<String>> params) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    if (fhirParams.getFirst("code").isEmpty() || fhirParams.getFirst("system").isEmpty()) {
      return new Parameters();
    }

    CodeSystemQueryParams csParams = new CodeSystemQueryParams()
        .setConceptCode(fhirParams.getFirst("code").orElse(null))
        .setConceptCodeSystemVersion(fhirParams.getFirst("version").orElse(null))
        .setUri(fhirParams.getFirst("system").orElse(null))
        .setVersionVersion(fhirParams.getFirst("version").orElse(null))
        .setVersionReleaseDateGe(fhirParams.getFirst("date").map(d -> LocalDateTime.parse(d).toLocalDate()).orElse(null))
        .setVersionExpirationDateLe(fhirParams.getFirst("date").map(d -> LocalDateTime.parse(d).toLocalDate()).orElse(null))
        .setVersionsDecorated(true).setConceptsDecorated(true).setPropertiesDecorated(true);
    Optional<CodeSystem> codeSystem = codeSystemService.query(csParams).findFirst();
    return mapper.toFhirParameters(codeSystem.orElse(null), fhirParams);
  }

  public Parameters validateCode(Map<String, List<String>> params) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    if (fhirParams.getFirst("code").isEmpty() || fhirParams.getFirst("url").isEmpty()) {
      return new Parameters();
    }

    ConceptQueryParams cParams = new ConceptQueryParams()
        .setCode(fhirParams.getFirst("code").orElse(null))
        .setCodeSystemVersion(fhirParams.getFirst("version").orElse(null))
        .setCodeSystemUri(fhirParams.getFirst("url").orElse(null));
    Optional<Concept> concept = conceptService.query(cParams).findFirst();
    return mapper.toFhirParameters(concept.orElse(null), fhirParams);
  }

  public Parameters subsumes(Map<String, List<String>> params, OperationOutcome outcome) {
    outcome.setIssue(new ArrayList<>());
    FhirQueryParams fhirParams = new FhirQueryParams(params);

    if (fhirParams.getFirst("codeA").isEmpty() || fhirParams.getFirst("codeB").isEmpty() || fhirParams.getFirst("system").isEmpty()) {
      outcome.getIssue().addAll(Stream.of("codeA", "codeB", "system").filter(key -> fhirParams.getFirst(key).isEmpty())
          .map(key -> new OperationOutcomeIssue()
              .setSeverity("error")
              .setCode("required")
              .setDetails(new CodeableConcept().setText(String.format("Parameter '%s' not provided", key)))).toList());
      return null;
    }

    CodeSystemQueryParams codeSystemParams = new CodeSystemQueryParams();
    codeSystemParams.setUri(fhirParams.getFirst("system").get());
    codeSystemParams.setLimit(1);
    Optional<CodeSystem> codeSystem = codeSystemService.query(codeSystemParams).findFirst();
    if (codeSystem.isEmpty()) {
      outcome.getIssue().add(new OperationOutcomeIssue()
          .setSeverity("error")
          .setCode("not-found")
          .setDetails(new CodeableConcept().setText(String.format("Code System with uri '%s' not found", fhirParams.getFirst("system").get()))
          ));
      return null;
    }

    String versionVersion = fhirParams.getFirst("version").isPresent() ? fhirParams.getFirst("version").get() :
        codeSystemVersionService.loadLastVersion(codeSystem.get().getId()).getVersion();
    Optional<Concept> conceptA = findConcept(fhirParams.getFirst("system").get(), fhirParams.getFirst("codeA").get(), versionVersion);
    if (conceptA.isEmpty()) {
      outcome.getIssue().add(new OperationOutcomeIssue().setSeverity("error").setCode("not-found")
          .setDetails(new CodeableConcept().setText(String.format("Concept  with code '%s' not found", fhirParams.getFirst("codeA").get()))));
      return null;
    }
    Optional<Concept> conceptB = findConcept(fhirParams.getFirst("system").get(), fhirParams.getFirst("codeB").get(), versionVersion);
    if (conceptB.isEmpty()) {
      outcome.getIssue().add(new OperationOutcomeIssue().setSeverity("error").setCode("not-found")
          .setDetails(new CodeableConcept().setText(String.format("Concept  with code '%s' not found", fhirParams.getFirst("codeB").get()))));
      return null;
    }
    return subsumes(conceptA.get(), conceptB.get());
  }

  public Parameters subsumes(Parameters params, OperationOutcome outcome) {
    outcome.setIssue(new ArrayList<>());
    Optional<ParametersParameter> codingA = params.getParameter().stream().filter(p -> p.getName().equals("codingA")).findFirst();
    Optional<ParametersParameter> codingB = params.getParameter().stream().filter(p -> p.getName().equals("codingB")).findFirst();
    if (codingA.isEmpty()) {
      outcome.getIssue().add(
          new OperationOutcomeIssue().setSeverity("error").setCode("required").setDetails(new CodeableConcept().setText("Parameter 'codingA' not provided")));
      return null;
    }
    if (codingB.isEmpty()) {
      outcome.getIssue().add(
          new OperationOutcomeIssue().setSeverity("error").setCode("required").setDetails(new CodeableConcept().setText("Parameter 'codingB' not provided")));
      return null;
    }

    String versionA = codingA.get().getValueCoding().getVersion() != null ? codingA.get().getValueCoding().getVersion() :
        codeSystemVersionService.loadLastVersionByUri(codingA.get().getValueCoding().getSystem()).getVersion();
    Optional<Concept> conceptA = findConcept(codingA.get().getValueCoding().getSystem(), codingA.get().getValueCoding().getCode(), versionA);
    if (conceptA.isEmpty()) {
      outcome.getIssue().add(new OperationOutcomeIssue().setSeverity("error").setCode("not-found")
          .setDetails(new CodeableConcept().setText(String.format("Concept with code '%s' not found", codingA.get().getValueCoding().getCode()))));
      return null;
    }

    String versionB = codingB.get().getValueCoding().getVersion() != null ? codingB.get().getValueCoding().getVersion() :
        codeSystemVersionService.loadLastVersionByUri(codingB.get().getValueCoding().getSystem()).getVersion();
    Optional<Concept> conceptB = findConcept(codingB.get().getValueCoding().getSystem(), codingB.get().getValueCoding().getCode(), versionB);
    if (conceptB.isEmpty()) {
      outcome.getIssue().add(new OperationOutcomeIssue().setSeverity("error").setCode("not-found")
          .setDetails(new CodeableConcept().setText(String.format("Concept with code '%s' not found", codingB.get().getValueCoding().getCode()))));
      return null;
    }

    return subsumes(conceptA.get(), conceptB.get());
  }

  private Parameters subsumes(Concept conceptA, Concept conceptB) {
    List<Long> codeAProperties = conceptA.getVersions().stream()
        .flatMap(entityVersion -> entityVersion.getPropertyValues().stream())
        .map(EntityPropertyValue::getEntityPropertyId).toList();
    List<Long> codeBProperties = conceptB.getVersions().stream()
        .flatMap(entityVersion -> entityVersion.getPropertyValues().stream())
        .map(EntityPropertyValue::getEntityPropertyId).toList();

    boolean subsumes = codeAProperties.stream().allMatch(ap -> codeBProperties.stream().anyMatch(bp -> bp.equals(ap)));
    boolean subsumedBy = codeBProperties.stream().allMatch(bp -> codeAProperties.stream().anyMatch(ap -> ap.equals(bp)));

    return new Parameters().setParameter(new ArrayList<>(List.of(
        new ParametersParameter()
            .setName("outcome")
            .setValueCode(subsumes && subsumedBy ? "equivalent" : subsumes ? "subsumes" : subsumedBy ? "subsumed-by" : "not-subsumed")
    )));

  }

  public Parameters findMatches(Parameters params, OperationOutcome outcome) {
    outcome.setIssue(new ArrayList<>());

    Optional<String> system = params.getParameter().stream().filter(p -> "system".equals(p.getName())).map(ParametersParameter::getValueString).findFirst();
    Optional<String> version = params.getParameter().stream().filter(p -> "version".equals(p.getName())).map(ParametersParameter::getValueString).findFirst();
    Optional<Boolean> exact = params.getParameter().stream().filter(p -> "exact".equals(p.getName())).map(ParametersParameter::getValueBoolean).findFirst();
    List<ParametersParameter> properties = params.getParameter().stream().filter(p -> "property".equals(p.getName())).toList();
    if (system.isEmpty() || CollectionUtils.isEmpty(properties)) {
      if (system.isEmpty()) {
        outcome.getIssue().add(
            new OperationOutcomeIssue().setSeverity("error").setCode("required").setDetails(new CodeableConcept().setText("Parameter system is not provided")));
      }
      if (CollectionUtils.isEmpty(properties)) {
        outcome.getIssue().add(new OperationOutcomeIssue().setSeverity("error").setCode("required")
            .setDetails(new CodeableConcept().setText("Parameter property is not provided")));
      }
      return null;
    }

    String propertyValues = properties.stream().map(p -> {
      Optional<String> property = p.getPart().stream().filter(part -> part.getName().equals("code")).map(ParametersParameter::getValueCode).findFirst();
      Optional<String> propertyValue = p.getPart().stream().filter(part -> part.getName().equals("value")).map(ParametersParameter::getValueString).findFirst();
      if (property.isEmpty()) {
        return null;
      }
      return property.get() + (propertyValue.isEmpty() ? "" : "|" + propertyValue.get());
    }).filter(Objects::nonNull).collect(Collectors.joining(","));

    ConceptQueryParams conceptParams = new ConceptQueryParams();
    conceptParams.setCodeSystemUri(system.get());
    conceptParams.setCodeSystemVersion(version.orElse(null));

    conceptParams.setPropertyValues(propertyValues);
    List<Concept> match = new ArrayList<>(conceptService.query(conceptParams).getData());

    if (exact.isEmpty() || !exact.get()) {
      conceptParams.setPropertyValues(null);
      conceptParams.setPropertyValuesPartial(propertyValues);
      match.addAll(conceptService.query(conceptParams).getData().stream()
          .filter(pm -> match.stream().noneMatch(em -> em.getId().equals(pm.getId())))
          .toList());
    }
    return new Parameters().setParameter(match.stream().map(c -> {
      Coding coding = new Coding();
      coding.setSystem(c.getCodeSystem());
      coding.setCode(c.getCode());
      coding.setExtension(c.getVersions().stream().filter(v -> !PublicationStatus.retired.equals(v.getStatus()))
          .flatMap(v -> v.getDesignations().stream().filter(d -> !PublicationStatus.retired.equals(d.getStatus()))
              .map(d -> new Extension().setValueAttachment(new Attachment().setLanguage(d.getLanguage()).setData(d.getName())))).toList());
      return new ParametersParameter().setName("match").setValueCoding(coding);
    }).toList());
  }

  private Optional<Concept> findConcept(String uri, String code, String version) {
    ConceptQueryParams conceptParams = new ConceptQueryParams();
    conceptParams.setCode(code);
    conceptParams.setCodeSystemUri(uri);
    conceptParams.setCodeSystemVersion(version);
    conceptParams.setLimit(1);
    return conceptService.query(conceptParams).findFirst();
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
      issue.setDetails(new CodeableConcept().setText("Code '" + fhirParams.getFirst("code").get() + "' not found"));
    }
    return new OperationOutcome(issue);
  }

  public com.kodality.zmei.fhir.resource.terminology.CodeSystem get(String codeSystemId, Map<String, List<String>> params) {
    String versionCode = Optional.ofNullable(params.getOrDefault("version", null)).map(v -> String.join(",", v)).orElse(null);

    CodeSystemQueryParams codeSystemParams = new CodeSystemQueryParams();
    codeSystemParams.setId(codeSystemId);
    codeSystemParams.setVersionVersion(versionCode);
    codeSystemParams.setPropertiesDecorated(true);
    codeSystemParams.setLimit(1);
    CodeSystem codeSystem = codeSystemService.query(codeSystemParams).findFirst().orElse(null);
    if (codeSystem == null) {
      return null;
    }
    CodeSystemVersion version = StringUtils.isEmpty(versionCode) ? codeSystemVersionService.loadLastVersion(codeSystemId) : codeSystemVersionService.load(codeSystemId, versionCode).orElse(null);
    if (version != null) {
      CodeSystemEntityVersionQueryParams codeSystemEntityVersionParams = new CodeSystemEntityVersionQueryParams().setCodeSystemVersionId(version.getId());
      codeSystemEntityVersionParams.all();
      version.setEntities(codeSystemEntityVersionService.query(codeSystemEntityVersionParams).getData());
    }
    return mapper.toFhir(codeSystem, version);

  }

  public Bundle search(Map<String, List<String>> params) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);
    CodeSystemQueryParams queryParams = new CodeSystemQueryParams();
    queryParams.setUri(fhirParams.getFirst("system").orElse(fhirParams.getFirst("url").orElse(null)));
    queryParams.setVersionVersion(fhirParams.getFirst("version").orElse(null));
    queryParams.setNameContains(fhirParams.getFirst("title").orElse(fhirParams.getFirst("name").orElse(null)));
    queryParams.setVersionStatus(fhirParams.getFirst("status").orElse(null));
    queryParams.setVersionSource(fhirParams.getFirst("publisher").orElse(null));
    queryParams.setDescriptionContains(fhirParams.getFirst("description").orElse(null));
    queryParams.setContent(fhirParams.getFirst("content-mode").orElse(null));
    queryParams.setConceptCode(fhirParams.getFirst("code").orElse(null));
    queryParams.setVersionsDecorated(true);
    queryParams.setPropertiesDecorated(true);
    queryParams.setLimit(fhirParams.getCount());
    List<CodeSystem> codeSystems = codeSystemService.query(queryParams).getData();
    return Bundle.of("searchset", codeSystems.stream()
        .flatMap(cs -> cs.getVersions().stream().map(csv -> {
          CodeSystemEntityVersionQueryParams codeSystemEntityVersionParams = new CodeSystemEntityVersionQueryParams()
              .setCodeSystemVersionId(csv.getId())
              .setCode(fhirParams.getFirst("code").orElse(null));
          codeSystemEntityVersionParams.all();
          csv.setEntities(codeSystemEntityVersionService.query(codeSystemEntityVersionParams).getData());
          return mapper.toFhir(cs, csv);
        })).collect(Collectors.toList()));
  }

  public void save(Optional<String> url, Optional<String> version, com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem) {
    if (url.isEmpty() || version.isEmpty()) {
      throw ApiError.TE712.toApiException();
    }
    codeSystem.setUrl(url.get());
    codeSystem.setVersion(version.get());
    fhirImportService.importCodeSystem(codeSystem);
  }
}
