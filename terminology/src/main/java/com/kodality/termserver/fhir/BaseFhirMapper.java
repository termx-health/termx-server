package com.kodality.termserver.fhir;

import com.kodality.kefhir.core.model.search.SearchCriterion;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public abstract class BaseFhirMapper {

  protected static Integer getOffset(SearchCriterion fhir) {
    return fhir.getCount() == null || fhir.getCount() == 0 ? 0 : (fhir.getPage() - 1) * fhir.getCount();
  }

  protected static Map<String, String> getSimpleParams(SearchCriterion fhir) {
    return fhir.getRawParams().keySet().stream()
        .filter(k -> CollectionUtils.isNotEmpty(fhir.getRawParams().get(k)))
        .collect(Collectors.toMap(k -> k, k -> fhir.getRawParams().get(k).get(0)));
  }

}
