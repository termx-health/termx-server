package com.kodality.termserver.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termserver.terminology.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Factory
@RequiredArgsConstructor
public class CodeSystemFindMatchesOperation implements TypeOperationDefinition {
  private final ConceptService conceptService;

  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  public String getOperationName() {
    return "find-matches";
  }


  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(Parameters req) {
    String system = req.findParameter("system").map(ParametersParameter::getValueString).orElse(null);
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);
    Boolean exact = req.findParameter("exact").map(ParametersParameter::getValueBoolean).orElse(null);
    List<String> properties = req.getParameter().stream().filter(p -> "property".equals(p.getName()))
        .map(p -> p.getPart("code").getValueCode() + p.findPart("value").map(pp -> "|" + pp.getValueString()).orElse(""))
        .toList();

    if (system == null) {
      throw new FhirException(400, IssueType.INVALID, "system required");
    }
    if (CollectionUtils.isEmpty(properties)) {
      throw new FhirException(400, IssueType.INVALID, "at least one property required");
    }

    ConceptQueryParams conceptParams = new ConceptQueryParams();
    conceptParams.setCodeSystemUri(system);
    conceptParams.setCodeSystemVersion(version);
    conceptParams.setPropertyValues(String.join(";", properties));
    List<Concept> match = new ArrayList<>(conceptService.query(conceptParams).getData());

    if (exact == null || !exact) {
      conceptParams.setPropertyValues(null);
      conceptParams.setPropertyValuesPartial(String.join(";", properties));
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
}
