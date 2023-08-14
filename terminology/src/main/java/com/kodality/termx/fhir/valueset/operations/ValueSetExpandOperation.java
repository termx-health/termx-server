package com.kodality.termx.fhir.valueset.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.fhir.valueset.ValueSetFhirMapper;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.ProvenanceService;
import com.kodality.termx.terminology.valueset.ValueSetService;
import com.kodality.termx.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.terminology.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
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
  private final ProvenanceService provenanceService;
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

    com.kodality.zmei.fhir.resource.terminology.ValueSet resp = expand(valueSet, versionNumber, null);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    com.kodality.zmei.fhir.resource.terminology.ValueSet resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public com.kodality.zmei.fhir.resource.terminology.ValueSet run(Parameters req) {
    String url = req.findParameter("url").map(pp -> pp.getValueUrl() != null ? pp.getValueUrl() : pp.getValueString())
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "parameter url required"));
    String versionNr = req.findParameter("valueSetVersion").map(ParametersParameter::getValueString).orElse(null);

    ValueSetQueryParams vsParams = new ValueSetQueryParams();
    vsParams.setUri(url);
    vsParams.setLimit(1);
    ValueSet valueSet = valueSetService.query(vsParams).findFirst()
        .orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "value set not found: " + url));

    return expand(valueSet, versionNr, req);
  }

  public com.kodality.zmei.fhir.resource.terminology.ValueSet expand(ValueSet vs, String versionNr, Parameters req) {
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
    List<Provenance> provenances = provenanceService.find("ValueSetVersion|" + version.getId());

    if (req == null) {
      return ValueSetFhirMapper.toFhir(vs, version, provenances, expandedConcepts, false);
    }


    boolean flat = req.findParameter("excludeNested").map(ParametersParameter::getValueBoolean).orElse(false);
    boolean active = req.findParameter("activeOnly").map(ParametersParameter::getValueBoolean).orElse(false);

    if (active) {
      expandedConcepts = expandedConcepts.stream().filter(ValueSetVersionConcept::isActive).toList();
    }
    return ValueSetFhirMapper.toFhir(vs, version, provenances, expandedConcepts, flat);
  }

}
