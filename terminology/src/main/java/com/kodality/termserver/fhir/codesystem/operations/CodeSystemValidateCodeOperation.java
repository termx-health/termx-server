package com.kodality.termserver.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
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
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

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
    String url = req.findParameter("url").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "url parameter required"));
    String code = req.findParameter("code").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "code parameter required"));
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);

    ConceptQueryParams cp = new ConceptQueryParams();
    cp.setCode(code);

    CodeSystemQueryParams csp = new CodeSystemQueryParams();
    csp.setUri(url);
    csp.setLimit(1);
    CodeSystem cs = codeSystemService.query(csp).findFirst().orElse(null);
    if (cs == null) {
      return error("CodeSystem not found by url " + url);
    }
    cp.setCodeSystem(cs.getId());

    if (version != null) {
      CodeSystemVersionQueryParams csvp = new CodeSystemVersionQueryParams();
      csvp.setCodeSystem(cp.getCodeSystem());
      csvp.setVersion(version);
      csvp.setStatus(PublicationStatus.active);
      csvp.setLimit(1);
      CodeSystemVersion csv = codeSystemVersionService.query(csvp).findFirst().orElse(null);
      if (csv == null) {
        return error("CodeSystem active version not found");
      }
      cp.setCodeSystemVersionId(csv.getId());
    }

    Concept concept = conceptService.query(cp).findFirst().orElse(null);
    if (concept == null) {
      return error("Code '" + code + "' is invalid");
    }

    String conceptDisplay = extractDisplay(concept);
    if (display != null && !display.equals(conceptDisplay)) {
      return new Parameters()
          .addParameter(new ParametersParameter("result").setValueBoolean(false))
          .addParameter(new ParametersParameter("display").setValueString(conceptDisplay))
          .addParameter(new ParametersParameter("message").setValueString("The display '" + display + "' is incorrect"));
    }

    return new Parameters()
        .addParameter(new ParametersParameter("result").setValueBoolean(true));
  }

  private static Parameters error(String message) {
    return new Parameters()
        .addParameter(new ParametersParameter("result").setValueBoolean(false))
        .addParameter(new ParametersParameter("message").setValueString(message));
  }

  public static String extractDisplay(Concept c) {
    return CollectionUtils.isEmpty(c.getVersions()) || c.getVersions().get(0).getDesignations() == null ? null :
        c.getVersions().get(0).getDesignations().stream().filter(Designation::isPreferred).findFirst().map(Designation::getName).orElse(null);
  }

}
