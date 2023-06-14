package com.kodality.termserver.fhir.valueset.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termserver.fhir.valueset.ValueSetFhirMapper;
import com.kodality.termserver.terminology.valueset.ValueSetService;
import com.kodality.termserver.terminology.valueset.ValueSetVersionService;
import com.kodality.termserver.terminology.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
@RequiredArgsConstructor
public class ValueSetExpandOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;

  public String getResourceType() {
    return ResourceType.ValueSet.name();
  }

  public String getOperationName() {
    return "expand";
  }

  public ResourceContent run(ResourceId id, ResourceContent p) {
    String[] parts = ValueSetFhirMapper.parseCompositeId(id.getResourceId());
    String vsId = parts[0];
    String versionNumber = parts[1];

    ValueSetQueryParams vsParams = new ValueSetQueryParams();
    vsParams.setId(vsId);
    vsParams.setLimit(1);
    ValueSet valueSet = valueSetService.query(vsParams).findFirst()
        .orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "value set not found: " + id.getResourceId()));

    com.kodality.zmei.fhir.resource.terminology.ValueSet resp = expand(valueSet, versionNumber);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    com.kodality.zmei.fhir.resource.terminology.ValueSet resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public com.kodality.zmei.fhir.resource.terminology.ValueSet run(Parameters req) {
    String url = req.findParameter("url").map(ParametersParameter::getValueUrl)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "parameter url required"));
    String versionNr = req.findParameter("valueSetVersion").map(ParametersParameter::getValueString).orElse(null);

    ValueSetQueryParams vsParams = new ValueSetQueryParams();
    vsParams.setUri(url);
    vsParams.setLimit(1);
    ValueSet valueSet = valueSetService.query(vsParams).findFirst()
        .orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "value set not found: " + url));
    return expand(valueSet, versionNr);
  }

  public com.kodality.zmei.fhir.resource.terminology.ValueSet expand(ValueSet vs, String versionNr) {
    ValueSetVersion version;
    if (versionNr != null) {
      ValueSetVersionQueryParams vsvParams = new ValueSetVersionQueryParams();
      vsvParams.setValueSet(vs.getId());
      vsvParams.setVersion(versionNr);
      vsvParams.setLimit(1);
      version = valueSetVersionService.query(vsvParams).findFirst().orElse(null);
    } else {
      version = valueSetVersionService.loadLastVersion(vs.getId());
    }
    if (version == null) {
      throw new FhirException(400, IssueType.NOTFOUND, "value set version not found");
    }

    List<ValueSetVersionConcept> expandedConcepts = valueSetVersionConceptService.expand(vs.getId(), version.getVersion(), null);
    return ValueSetFhirMapper.toFhir(vs, version, expandedConcepts);
  }

}
