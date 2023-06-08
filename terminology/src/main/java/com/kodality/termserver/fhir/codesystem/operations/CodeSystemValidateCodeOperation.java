package com.kodality.termserver.fhir.codesystem.operations;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termserver.terminology.codesystem.CodeSystemService;
import com.kodality.termserver.terminology.codesystem.CodeSystemVersionService;
import com.kodality.termserver.terminology.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import io.micronaut.context.annotation.Factory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.r4.model.ResourceType;

@Slf4j
@Factory
@RequiredArgsConstructor
public class CodeSystemValidateCodeOperation implements TypeOperationDefinition {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;

  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  public String getOperationName() {
    return "validate-code";
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(Parameters req) {
    String code = req.findParameter("code").map(ParametersParameter::getValueString).orElse(null);
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);
    String url = req.findParameter("url").map(ParametersParameter::getValueString).orElse(null);
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);

    if (code == null) {
      throw new ApiClientException("'code' parameter required");
    }
    ConceptQueryParams cp = new ConceptQueryParams();
    cp.setCode(code);

    if (url != null) {
      CodeSystemQueryParams csp = new CodeSystemQueryParams();
      csp.setUri(url);
      csp.setLimit(1);
      CodeSystem cs = codeSystemService.query(csp).findFirst().orElse(null);
      if (cs == null) {
        throw new NotFoundException("CodeSystem not found by url " + url);
      }
      cp.setCodeSystem(cs.getId());
    }

    if (version != null) {
      CodeSystemVersionQueryParams csvp = new CodeSystemVersionQueryParams();
      csvp.setCodeSystem(cp.getCodeSystem());
      csvp.setVersion(version);
      csvp.setStatus(PublicationStatus.active);
      csvp.setLimit(1);
      CodeSystemVersion csv = codeSystemVersionService.query(csvp).findFirst().orElse(null);
      if (csv == null) {
        throw new NotFoundException("CodeSystem active version not found");
      }
      cp.setCodeSystemVersionId(csv.getId());
    }

    Concept concept = conceptService.query(cp).findFirst().orElse(null);
    if (concept == null) {
      Parameters parameters = new Parameters();
      parameters.addParameter(new ParametersParameter().setName("result").setValueBoolean(false));
      parameters.addParameter(new ParametersParameter().setName("message").setValueString("Code '" + code + "' is invalid"));
      return parameters;
    }

    String conceptDisplay = extractDisplay(concept);
    if (display != null && !display.equals(conceptDisplay)) {
      Parameters parameters = new Parameters();
      parameters.addParameter(new ParametersParameter().setName("result").setValueBoolean(false));
      parameters.addParameter(new ParametersParameter().setName("display").setValueString(conceptDisplay));
      parameters.addParameter(new ParametersParameter().setName("message").setValueString("The display '" + display + "' is incorrect"));
      return parameters;
    }

    Parameters parameters = new Parameters();
    parameters.addParameter(new ParametersParameter().setName("result").setValueBoolean(true));
    return parameters;
  }

  public static String extractDisplay(Concept c) {
    return CollectionUtils.isEmpty(c.getVersions()) || c.getVersions().get(0).getDesignations() == null ? null :
        c.getVersions().get(0).getDesignations().stream().filter(Designation::isPreferred).findFirst().map(Designation::getName).orElse(null);
  }

}
