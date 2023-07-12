package com.kodality.termx.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.terminology.codesystem.CodeSystemService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
@RequiredArgsConstructor
public class CodeSystemLookupOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final CodeSystemService codeSystemService;

  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  public String getOperationName() {
    return "lookup";
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent parameters) {
    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String[] parts = CodeSystemFhirMapper.parseCompositeId(id.getResourceId());
    String csId = parts[0];
    String versionNumber = parts[1];
    Parameters resp = run(csId, null, versionNumber, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent c) {
    Parameters req = FhirMapper.fromJson(c.getValue(), Parameters.class);
    String system = req.findParameter("system").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "system parameter required"));
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);

    Parameters resp = run(null, system, version, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(String csId, String system, String version, Parameters req) {
    String code = req.findParameter("code").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "code parameter required"));
    LocalDate date = req.findParameter("date").map(pp -> LocalDateTime.parse(pp.getValueString()).toLocalDate()).orElse(null);
    List<String> properties = req.getParameter().stream().filter(p -> "property".equals(p.getName())).map(ParametersParameter::getValueString).toList();

    CodeSystemQueryParams csParams = new CodeSystemQueryParams()
        .setId(csId)
        .setConceptCode(code)
        .setConceptCodeSystemVersion(version)
        .setUri(system)
        .setVersionVersion(version)
        .setVersionReleaseDateGe(date)
        .setVersionExpirationDateLe(date)
        .setVersionsDecorated(true).setConceptsDecorated(true).setPropertiesDecorated(true)
        .limit(1);
    CodeSystem cs = codeSystemService.query(csParams).findFirst()
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "Concept not found"));

    Parameters resp = new Parameters();
    resp.addParameter(new ParametersParameter().setName("name")
        .setValueString(cs.getId()));
    resp.addParameter(new ParametersParameter().setName("version")
        .setValueString(CollectionUtils.isEmpty(cs.getVersions()) ? null : cs.getVersions().get(0).getVersion()));
    resp.addParameter(new ParametersParameter().setName("display")
        .setValueString(getDesignations(cs).filter(Designation::isPreferred).findFirst().map(Designation::getName).orElse(null)));
    getDesignations(cs).filter(d -> !d.isPreferred()).forEach(d -> {
      resp.addParameter(new ParametersParameter("designation")
          .addPart(new ParametersParameter("value").setValueString(d.getName()))
          .addPart(new ParametersParameter("language").setValueString(d.getLanguage()))
      );
    });
    cs.getProperties().stream().filter(p -> CollectionUtils.isEmpty(properties) || properties.contains(p.getName())).forEach(p -> {
      findPropertyValues(cs, p.getId()).forEach(pv -> {
        resp.addParameter(new ParametersParameter("property")
            .addPart(new ParametersParameter("code").setValueString(p.getName()))
            .addPart(new ParametersParameter("description").setValueString(p.getDescription()))
            .addPart(toParameter(p.getType(), pv))
        );
      });
    });
    return resp;
  }

  private static Stream<EntityPropertyValue> findPropertyValues(CodeSystem cs, Long propertyId) {
    if (cs.getConcepts() == null || cs.getConcepts().size() == 0 ||
        cs.getConcepts().get(0).getVersions() == null || cs.getConcepts().get(0).getVersions().size() == 0 ||
        cs.getConcepts().get(0).getVersions().get(0).getPropertyValues() == null) {
      return Stream.of();
    }
    return cs.getConcepts().get(0).getVersions().get(0).getPropertyValues().stream().filter(pv -> pv.getEntityPropertyId().equals(propertyId));
  }

  private static ParametersParameter toParameter(String type, EntityPropertyValue pv) {
    ParametersParameter result = new ParametersParameter("value");
    switch (type) {
      case "code" -> result.setValueCode((String) pv.getValue());
      case "string" -> result.setValueString((String) pv.getValue());
      case "boolean" -> result.setValueBoolean((Boolean) pv.getValue());
      case "dateTime" -> result.setValueDateTime((OffsetDateTime) pv.getValue());
      case "decimal" -> result.setValueDecimal((BigDecimal) pv.getValue());
//            //TODO value type coding
    }
    return result;
  }

  private static Stream<Designation> getDesignations(CodeSystem cs) {
    return CollectionUtils.isEmpty(cs.getConcepts()) ? Stream.of() : getDesignations(cs.getConcepts().get(0));
  }

  private static Stream<Designation> getDesignations(Concept c) {
    return CollectionUtils.isEmpty(c.getVersions()) || c.getVersions().get(0).getDesignations() == null ? Stream.of() :
        c.getVersions().get(0).getDesignations().stream();
  }
}