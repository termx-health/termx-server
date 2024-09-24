package com.kodality.termx.terminology.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
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
public class CodeSystemValidateCodeOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;

  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  public String getOperationName() {
    return "validate-code";
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent parameters) {
    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String[] parts = CodeSystemFhirMapper.parseCompositeId(id.getResourceId());
    String csId = parts[0];
    String versionNumber = parts[1];
    CodeSystemVersion csv = codeSystemVersionService.load(csId, versionNumber)
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "Concept version not found"));
    Parameters resp = run(csv.getCodeSystem(), csv.getId(), req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(Parameters req) {
    String url = req.findParameter("url").map(ParametersParameter::getValueUrl)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "url parameter required"));
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);

    CodeSystemQueryParams csp = new CodeSystemQueryParams();
    csp.setUri(url);
    csp.setLimit(1);
    CodeSystem cs = codeSystemService.query(csp).findFirst().orElse(null);
    if (cs == null) {
      return error("CodeSystem not found by url " + url);
    }

    CodeSystemVersion csv = null;
    if (version != null) {
      CodeSystemVersionQueryParams csvp = new CodeSystemVersionQueryParams();
      csvp.setCodeSystem(cs.getId());
      csvp.setVersion(version);
      csvp.setStatus(PublicationStatus.active);
      csvp.setLimit(1);
      csv = codeSystemVersionService.query(csvp).findFirst().orElse(null);
      if (csv == null) {
        return error("CodeSystem active version not found");
      }
    }

    return run(cs.getId(), csv == null ? null : csv.getId(), req);
  }

  private Parameters run(String csId, Long versionId, Parameters req) {
    String code = req.findParameter("code").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "code parameter required"));
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);


    ConceptQueryParams cp = new ConceptQueryParams();
    cp.setCode(code);
    cp.setCodeSystem(csId);
    cp.setCodeSystemVersionId(versionId);
    cp.setPermittedCodeSystems(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW));

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
