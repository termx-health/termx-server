package com.kodality.termx.terminology.fhir.codesystem.operations;

import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptUtil;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
@RequiredArgsConstructor
public class CodeSystemLookupOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final ConceptService conceptService;
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
    Parameters resp = run(csId, versionNumber, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent c) {
    Parameters req = FhirMapper.fromJson(c.getValue(), Parameters.class);
    String system = req.findParameter("system").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "system parameter required"));
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);

    String csId = codeSystemService.query(new CodeSystemQueryParams().setUri(system).limit(1)).findFirst().map(CodeSystem::getId)
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "CodeSystem not found"));
    Parameters resp = run(csId, version, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(String csId, String version, Parameters req) {
    String code = req.findParameter("code").map(ParametersParameter::getValueString)
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "code parameter required"));
    LocalDate date = req.findParameter("date").map(pp -> LocalDateTime.parse(pp.getValueString()).toLocalDate()).orElse(null);



    ConceptQueryParams cQueryParams = new ConceptQueryParams()
        .setCodeSystem(csId)
        .setCodeEq(code)
        .setCodeSystemVersion(version)
        .setCodeSystemVersionReleaseDateGe(date)
        .setCodeSystemVersionExpirationDateLe(date)
        .limit(1);
    Concept c = conceptService.query(cQueryParams).findFirst()
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "Concept not found"));

    Parameters resp = new Parameters();
    resp.addParameter(new ParametersParameter().setName("name").setValueString(c.getCodeSystem()));


    Optional<CodeSystemVersionReference> csVersion = c.getVersions().stream().findFirst().flatMap(v -> Optional.ofNullable(v.getVersions()).orElse(List.of()).stream().findFirst());
    if (csVersion.isPresent()) {
      resp.addParameter(new ParametersParameter().setName("version").setValueString(csVersion.map(CodeSystemVersionReference::getVersion).orElse(null)));
    }

    String preferredLanguage = csVersion.map(CodeSystemVersionReference::getPreferredLanguage).orElse(null);
    List<Designation> designations = c.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getDesignations).orElse(List.of());
    Designation display = ConceptUtil.getDisplay(designations, preferredLanguage, List.of());
    resp.addParameter(new ParametersParameter().setName("display").setValueString(display != null ? display.getName() : null));
    designations.stream().filter(d -> display != d).forEach(d -> {
      resp.addParameter(new ParametersParameter("designation")
          .addPart(new ParametersParameter("use").setValueCoding(new Coding(d.getDesignationType())))
          .addPart(new ParametersParameter("value").setValueString(d.getName()))
          .addPart(new ParametersParameter("language").setValueString(d.getLanguage()))
      );
    });

    List<String> properties = req.getParameter().stream().filter(p -> "property".equals(p.getName())).map(ParametersParameter::getValueString).toList();
    List<EntityPropertyValue> propertyValues = c.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getPropertyValues).orElse(List.of());
    propertyValues.stream().filter(pv -> properties.contains(pv.getEntityProperty())).forEach(pv -> {
      resp.addParameter(new ParametersParameter("property")
          .addPart(new ParametersParameter("code").setValueString(pv.getEntityProperty()))
          .addPart(toParameter(pv.getEntityPropertyType(), pv)));
    });
    return resp;
  }

  private static ParametersParameter toParameter(String type, Object value) {
    ParametersParameter result = new ParametersParameter("value");
    switch (type) {
      case EntityPropertyType.code -> result.setValueCode((String) value);
      case EntityPropertyType.string -> result.setValueString((String) value);
      case EntityPropertyType.bool -> result.setValueBoolean((Boolean) value);
      case EntityPropertyType.decimal -> result.setValueDecimal(new BigDecimal(String.valueOf(value)));
      case EntityPropertyType.integer -> result.setValueInteger(Integer.valueOf(String.valueOf(value)));
      case EntityPropertyType.coding -> {
        Concept concept = JsonUtil.getObjectMapper().convertValue(value, Concept.class);
        result.setValueCoding(new Coding(concept.getCodeSystem(), concept.getCode()));
      }
      case EntityPropertyType.dateTime -> {
        if (value instanceof OffsetDateTime) {
          result.setValueDateTime((OffsetDateTime) value);
        } else {
          result.setValueDateTime(DateUtil.parseOffsetDateTime((String) value));
        }
      }
    }
    return result;
  }
}
