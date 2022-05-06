package com.kodality.termserver.fhir.codesystem;

import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemFhirMapper {

  public Parameters toFhirParameters(CodeSystem cs, FhirQueryParams fhirParams) {
    Parameters parameters = new Parameters();
    if (cs != null) {
      List<Parameter> parameter = new ArrayList<>();
      parameter.add(new Parameter().setName("name").setValueString(cs.getId()));
      parameter.add(new Parameter().setName("version").setValueString(extractVersion(cs)));
      parameter.add(new Parameter().setName("display").setValueString(extractDisplay(cs)));
      parameter.addAll(extractDesignations(cs));
      parameter.addAll(extractProperties(cs, fhirParams.get("property")));
      parameters.setParameter(parameter);
    }
    return parameters;
  }

  public Parameters toFhirParameters(Concept c, FhirQueryParams fhirParams) {
    Parameters parameters = new Parameters();
    if (c != null) {
      List<Parameter> parameter = new ArrayList<>();
      String conceptDisplay = extractDisplay(c);
      boolean result = fhirParams.getFirst("display").map(d -> d.equals(conceptDisplay)).orElse(true);
      parameter.add(new Parameter().setName("result").setValueBoolean(result));
      parameter.add(new Parameter().setName("display").setValueString(conceptDisplay));
      if (!result) {
        parameter.add(new Parameter().setName("message").setValueString("The display '" + fhirParams.getFirst("display").get() + "' is incorrect"));
      }
      parameters.setParameter(parameter);
    }
    return parameters;
  }

  private String extractVersion(CodeSystem cs) {
    if (cs.getVersions() == null || cs.getVersions().size() == 0) {
      return null;
    }
    return cs.getVersions().get(0).getVersion();
  }

  private String extractDisplay(CodeSystem cs) {
    if (!hasDesignations(cs)) {
      return null;
    }
    return cs.getConcepts().get(0).getVersions().get(0).getDesignations().stream()
        .filter(Designation::isPreferred).findFirst()
        .map(Designation::getName).orElse(null);
  }

  private List<Parameter> extractDesignations(CodeSystem cs) {
    if (!hasDesignations(cs)) {
      return new ArrayList<>();
    }
    return cs.getConcepts().get(0).getVersions().get(0).getDesignations().stream()
        .filter(d -> !d.isPreferred())
        .map(d -> {
          List<Parameter> part = new ArrayList<>();
          part.add(new Parameter().setName("value").setValueString(d.getName()));
          part.add(new Parameter().setName("language").setValueCode(d.getLanguage()));
          return new Parameter().setName("designation").setPart(part);
        }).collect(Collectors.toList());
  }

  private List<Parameter> extractProperties(CodeSystem cs, List<String> properties) {
    if (!hasProperties(cs)) {
      return new ArrayList<>();
    }
    return cs.getConcepts().get(0).getVersions().get(0).getPropertyValues().stream()
        .map(p -> {
          List<Parameter> part = new ArrayList<>();
          Optional<EntityProperty> property = cs.getProperties().stream()
              .filter(pr -> (CollectionUtils.isEmpty(properties) || properties.contains(pr.getName())) && pr.getId().equals(p.getEntityPropertyId()))
              .findFirst();
          if (property.isPresent()) {
            part.add(new Parameter().setName("code").setValueCode(property.get().getName()));
            part.add(new Parameter().setName("description").setValueCode(property.get().getDescription()));
            if (property.get().getType().equals("code")) {
              part.add(new Parameter().setName("value").setValueCode((String) p.getValue()));
            }
            if (property.get().getType().equals("string")) {
              part.add(new Parameter().setName("value").setValueString((String) p.getValue()));
            }
            if (property.get().getType().equals("boolean")) {
              part.add(new Parameter().setName("value").setValueBoolean((Boolean) p.getValue()));
            }
            if (property.get().getType().equals("dateTime")) {
              part.add(new Parameter().setName("value").setValueDateTime((OffsetDateTime) p.getValue()));
            }
            if (property.get().getType().equals("decimal")) {
              part.add(new Parameter().setName("value").setValueDecimal((BigDecimal) p.getValue()));
            }
            //TODO value type coding
          }
          return new Parameter().setName("property").setPart(part);
        }).collect(Collectors.toList());
  }

  private String extractDisplay(Concept c) {
    if (!hasDesignations(c)) {
      return null;
    }
    return c.getVersions().get(0).getDesignations().stream()
        .filter(Designation::isPreferred).findFirst()
        .map(Designation::getName).orElse(null);
  }

  private boolean hasDesignations(CodeSystem cs) {
    return !(cs.getConcepts() == null || cs.getConcepts().size() == 0 ||
        cs.getConcepts().get(0).getVersions() == null || cs.getConcepts().get(0).getVersions().size() == 0 ||
        cs.getConcepts().get(0).getVersions().get(0).getDesignations() == null);
  }

  private boolean hasProperties(CodeSystem cs) {
    return !(cs.getConcepts() == null || cs.getConcepts().size() == 0 ||
        cs.getConcepts().get(0).getVersions() == null || cs.getConcepts().get(0).getVersions().size() == 0 ||
        cs.getConcepts().get(0).getVersions().get(0).getPropertyValues() == null);
  }

  private boolean hasDesignations(Concept c) {
    return !(c.getVersions() == null || c.getVersions().size() == 0 || c.getVersions().get(0).getDesignations() == null);
  }
}
