package com.kodality.termx.fhir.conceptmap.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.fhir.conceptmap.ConceptMapFhirMapper;
import com.kodality.termx.terminology.mapset.MapSetService;
import com.kodality.termx.terminology.mapset.association.MapSetAssociationService;
import com.kodality.termx.terminology.mapset.version.MapSetVersionService;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptMapTranslateOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetAssociationService mapSetAssociationService;

  public String getResourceType() {
    return ResourceType.ConceptMap.name();
  }

  public String getOperationName() {
    return "translate";
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent parameters) {
    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String[] parts = ConceptMapFhirMapper.parseCompositeId(id.getResourceId());
    String cmId = parts[0];
    String versionNumber = parts[1];
    Parameters resp = run(cmId, null, versionNumber, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    String url = req.findParameter("url").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "url parameter required"));
    String conceptMapVersion = req.findParameter("conceptMapVersion").map(ParametersParameter::getValueString).orElse(null);
    Parameters resp = run(null, url, conceptMapVersion, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(String cmId, String cmUrl, String cmVersion, Parameters req) {
    String sourceCode = req.findParameter("sourceCode").map(c -> c.getValueCode() == null ? c.getValueString() : c.getValueCode()).orElse(null);
    String targetCode = req.findParameter("targetCode").map(c -> c.getValueCode() == null ? c.getValueString() : c.getValueCode()).orElse(null);
    String sourceSystem = req.findParameter("system").map(c -> c.getValueUri() == null ? c.getValueString() : c.getValueUri()).orElse(null);
    String targetSystem = req.findParameter("targetSystem").map(c -> c.getValueUri() == null ? c.getValueString() : c.getValueUri()).orElse(null);

    if (req.findParameter("sourceCoding").isPresent()) {
      sourceCode = req.findParameter("sourceCoding").map(ParametersParameter::getValueCoding).map(Coding::getCode).orElse(null);
      sourceSystem = req.findParameter("sourceCoding").map(ParametersParameter::getValueCoding).map(Coding::getSystem).orElse(null);
    }

    if (req.findParameter("targetCoding").isPresent()) {
      targetCode = req.findParameter("targetCoding").map(ParametersParameter::getValueCoding).map(Coding::getCode).orElse(null);
      targetSystem = req.findParameter("targetCoding").map(ParametersParameter::getValueCoding).map(Coding::getSystem).orElse(null);
    }

    if (req.findParameter("sourceCodeableConcept").isPresent()) {
      Optional<Coding> coding = req.findParameter("sourceCodeableConcept").map(ParametersParameter::getValueCodeableConcept)
          .flatMap(c -> c.getCoding().stream().findFirst());
      sourceCode = coding.map(Coding::getCode).orElse(null);
      sourceSystem = coding.map(Coding::getSystem).orElse(null);
    }

    if (req.findParameter("targetCodeableConcept").isPresent()) {
      Optional<Coding> coding = req.findParameter("targetCodeableConcept").map(ParametersParameter::getValueCodeableConcept)
          .flatMap(c -> c.getCoding().stream().findFirst());
      targetCode = coding.map(Coding::getCode).orElse(null);
      targetSystem = coding.map(Coding::getSystem).orElse(null);
    }

    if (sourceCode == null && targetCode == null) {
      throw new FhirException(400, IssueType.INVALID, "One (and only one) of the in parameters (sourceCode, sourceCoding, sourceCodeableConcept, targetCode, targetCoding, or targetCodeableConcept) SHALL be provided, to identify the code that is to be translated.");
    }
    if (sourceCode != null && sourceSystem == null) {
      throw new FhirException(400, IssueType.INVALID, "If a source code is provided, a system must be provided.");
    }
    if (targetCode != null && targetSystem == null) {
      throw new FhirException(400, IssueType.INVALID, "If a target code is provided, a system must be provided.");
    }

    MapSetQueryParams msParams = new MapSetQueryParams()
        .setId(cmId)
        .setUri(cmUrl)
        .setVersionVersion(cmVersion)
        .setVersionsDecorated(cmVersion != null)
        .setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.MS_VIEW))
        .limit(1);
    MapSet mapSet = mapSetService.query(msParams).findFirst().orElse(null);
    if (mapSet == null) {
      return new Parameters().addParameter(new ParametersParameter("result").setValueBoolean(false));
    }

    MapSetVersion mapSetVersion = Optional.ofNullable(mapSet.getVersions()).orElse(List.of()).stream().findFirst().orElse(mapSetVersionService.loadLastVersion(mapSet.getId()));
    if (mapSetVersion == null) {
      return new Parameters().addParameter(new ParametersParameter("result").setValueBoolean(false));
    }

    MapSetAssociationQueryParams aParams = new MapSetAssociationQueryParams().setMapSetVersionId(mapSetVersion.getId())
        .setSourceCodeAndSystemUri(sourceCode == null ? null : sourceCode + "|" + sourceSystem)
        .setTargetCodeAndSystemUri(targetCode == null ? null : targetCode + "|" + targetSystem)
        .setNoMap(false)
        .limit(-1);
    List<MapSetAssociation> associations = mapSetAssociationService.query(aParams).getData();
    if (CollectionUtils.isEmpty(associations)) {
      return new Parameters().addParameter(new ParametersParameter("result").setValueBoolean(false));
    }

    Parameters p = new Parameters();
    p.addParameter(new ParametersParameter("result").setValueBoolean(true));
    associations.forEach(a -> p.addParameter(new ParametersParameter("match")
        .addPart(new ParametersParameter("equivalence").setValueCode(a.getRelationship()))
        .addPart(new ParametersParameter("concept").setValueCoding(new Coding()
            .setCode(aParams.getSourceCodeAndSystemUri() == null ? a.getSource().getCode() : a.getTarget().getCode())
            .setSystem(aParams.getSourceCodeAndSystemUri() == null ? a.getSource().getCodeSystemUri() : a.getTarget().getCodeSystemUri())))
    ));
    return p;
  }


}
