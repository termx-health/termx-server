package com.kodality.termserver.ts.valueset;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetQueryParams;
import com.kodality.termserver.ts.mapset.MapSetService;
import com.kodality.termserver.ts.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import com.kodality.termserver.valueset.ValueSetVersionRuleQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetDeleteService {
  private final MapSetService mapSetService;
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;

  @Transactional
  public void deleteValueSet(String valueSet) {
    checkValueSetUsed(valueSet);
    deleteValueSetVersions(valueSet);
    valueSetService.cancel(valueSet);
  }

  private void checkValueSetUsed(String valueSet) {
    List<String> requiredCodeSystems = List.of("codesystem-content-mode", "concept-property-type", "contact-point-system", "contact-point-use",
        "filter-operator", "languages", "namingsystem-identifier-type", "namingsystem-type", "publication-status");
    if (requiredCodeSystems.contains(valueSet)) {
      throw ApiError.TE303.toApiException();
    }

    ValueSetVersionRuleQueryParams valueSetVersionRuleParams = new ValueSetVersionRuleQueryParams().setValueSet(valueSet);
    valueSetVersionRuleParams.setLimit(0);
    if (valueSetVersionRuleService.query(valueSetVersionRuleParams).getMeta().getTotal() > 0) {
      throw ApiError.TE305.toApiException();
    }

    MapSetQueryParams mapSetParams = new MapSetQueryParams();
    mapSetParams.setSourceValueSet(valueSet);
    mapSetParams.setLimit(1);
    Optional<MapSet> mapSet = mapSetService.query(mapSetParams).findFirst();
    if (mapSet.isPresent()) {
      throw ApiError.TE304.toApiException(Map.of("mapSet", mapSet.get().getId()));
    }
    mapSetParams.setSourceValueSet(null);
    mapSetParams.setTargetValueSet(valueSet);
    mapSet = mapSetService.query(mapSetParams).findFirst();
    if (mapSet.isPresent()) {
      throw ApiError.TE304.toApiException(Map.of("mapSet", mapSet.get().getId()));
    }
  }

  private void deleteValueSetVersions(String valueSet) {
    ValueSetVersionQueryParams valueSetVersionParams = new ValueSetVersionQueryParams().setValueSet(valueSet);
    valueSetVersionParams.all();
    List<ValueSetVersion> valueSetVersions = valueSetVersionService.query(valueSetVersionParams).getData();

    checkValueSetVersionsUsed(valueSetVersions);
    valueSetVersions.forEach(v -> valueSetVersionService.cancel(v.getId(), valueSet));
  }

  private void checkValueSetVersionsUsed(List<ValueSetVersion> valueSetVersions) {
    if (CollectionUtils.isEmpty(valueSetVersions)) {
      return;
    }
    ValueSetVersionRuleQueryParams valueSetVersionRuleParams = new ValueSetVersionRuleQueryParams();
    valueSetVersionRuleParams.setValueSetVersionIds(valueSetVersions.stream().map(v -> String.valueOf(v.getId())).collect(Collectors.joining(",")));
    valueSetVersionRuleParams.setLimit(0);
    if (valueSetVersionRuleService.query(valueSetVersionRuleParams).getMeta().getTotal() > 0) {
      throw ApiError.TE306.toApiException();
    }
  }
}
