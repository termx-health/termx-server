package com.kodality.termx.terminology.fhir.codesystem.operations;

import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.fhir.BaseFhirMapper;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
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
        .setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW))
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
      findPropertyValues(cs, p).forEach(pv -> resp.addParameter(new ParametersParameter("property")
          .addPart(new ParametersParameter("code").setValueString(p.getName()))
          .addPart(new ParametersParameter("description").setValueString(BaseFhirMapper.toFhirName(p.getDescription(), req.getLanguage())))
          .addPart(toParameter(p.getType(), pv))
      ));
    });
    return resp;
  }

  private static Stream<Object> findPropertyValues(CodeSystem cs, EntityProperty p) {
    if (cs.getConcepts() == null || cs.getConcepts().isEmpty() ||
        cs.getConcepts().get(0).getVersions() == null || cs.getConcepts().get(0).getVersions().isEmpty()) {
      return Stream.of();
    }
    Stream<Object> propertyValues = cs.getConcepts().get(0).getVersions().get(0).getPropertyValues() == null ? Stream.of() :
        cs.getConcepts().get(0).getVersions().get(0).getPropertyValues().stream()
        .filter(pv -> pv.getEntityPropertyId().equals(p.getId())).map(EntityPropertyValue::getValue);
    Stream<Object> associationValues = cs.getConcepts().get(0).getVersions().get(0).getAssociations() == null ||
        !List.of("is-a", "parent", "partOf", "groupedBy", "classifiedWith").contains(p.getName()) ? Stream.of() :
        cs.getConcepts().get(0).getVersions().get(0).getAssociations().stream().map(CodeSystemAssociation::getTargetCode);
    return Stream.concat(propertyValues, associationValues);
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

  private static Stream<Designation> getDesignations(CodeSystem cs) {
    return CollectionUtils.isEmpty(cs.getConcepts()) ? Stream.of() : getDesignations(cs.getConcepts().get(0));
  }

  private static Stream<Designation> getDesignations(Concept c) {
    return CollectionUtils.isEmpty(c.getVersions()) || c.getVersions().get(0).getDesignations() == null ? Stream.of() :
        c.getVersions().get(0).getDesignations().stream();
  }
}
