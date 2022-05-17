package com.kodality.termserver.fhir.conceptmap;

import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.ts.codesystem.CodeSystemService;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ConceptMapFhirMapper {
  private final CodeSystemService codeSystemService;

  public Parameters toFhirParameters(MapSet ms) {
    Parameters parameters = new Parameters();
    if (ms != null) {
      List<Parameter> parameter = new ArrayList<>();
      List<Parameter> matches = extractMatches(ms);
      parameter.add(new Parameter().setName("result").setValueBoolean(CollectionUtils.isNotEmpty(matches)));
      parameter.addAll(matches);
      parameters.setParameter(parameter);
    } else {
      List<Parameter> parameter = new ArrayList<>();
      parameter.add(new Parameter().setName("result").setValueBoolean(false));
      parameters.setParameter(parameter);
    }
    return parameters;
  }

  private List<Parameter> extractMatches(MapSet ms) {
    if (ms.getAssociations() == null) {
      return new ArrayList<>();
    }
    return ms.getAssociations().stream().map(association -> {
      List<Parameter> parts = new ArrayList<>();
      String csUri = codeSystemService.query(new CodeSystemQueryParams().setCodeSystemEntityVersionId(association.getTarget().getId())).findFirst().map(CodeSystem::getUri).orElse(null);
      parts.add(new Parameter().setName("equivalence").setValueCode(association.getAssociationType()));
      parts.add(new Parameter().setName("concept").setValueCoding(new Coding().setCode(association.getTarget().getCode()).setSystem(csUri)));
      return new Parameter().setName("match").setPart(parts);
    }).collect(Collectors.toList());
  }
}
