package com.kodality.termx.terminology.terminology.valueset;

import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import io.micronaut.core.util.CollectionUtils;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetDuplicateService {
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;

  @Transactional
  public ValueSetVersion duplicateValueSetVersion(String targetVersionVersion, String targetValueSet, String sourceVersionVersion, String sourceValueSet) {
    ValueSetVersion version = valueSetVersionService.load(sourceValueSet, sourceVersionVersion).orElseThrow(() ->
        ApiError.TE301.toApiException(Map.of("version", sourceVersionVersion, "valueSet", sourceValueSet)));

    version.setId(null);
    version.setVersion(targetVersionVersion);
    version.setValueSet(targetValueSet);
    version.setStatus(PublicationStatus.draft);
    version.setCreated(null);
    if (version.getRuleSet() != null) {
      version.getRuleSet().setId(null);
    }
    valueSetVersionService.save(version);

    if (version.getRuleSet() != null && CollectionUtils.isNotEmpty(version.getRuleSet().getRules())) {
      version.getRuleSet().getRules().forEach(r -> r.setId(null));
      valueSetVersionRuleService.save(version.getRuleSet().getRules(), targetValueSet, targetVersionVersion);
    }

    return version;
  }
}
