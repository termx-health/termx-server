package com.kodality.termx.terminology.valueset;

import com.kodality.termx.ApiError;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.terminology.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetImportAction;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ValueSetImportService {
  private final ValueSetService valueSetService;
  private final CodeSystemService codeSystemService;
  private final ValueSetVersionService valueSetVersionService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;

  private final UserPermissionService userPermissionService;

  @Transactional
  public ValueSet importValueSet(ValueSet valueSet, ValueSetImportAction action) {
    userPermissionService.checkPermitted(valueSet.getId(), "ValueSet", "edit");

    long start = System.currentTimeMillis();
    log.info("IMPORT STARTED : value set - {}", valueSet.getId());
    prepare(valueSet);

    saveValueSet(valueSet);
    ValueSetVersion valueSetVersion = valueSet.getVersions().get(0);
    saveValueSetVersion(valueSetVersion);

    if (action.isActivate()) {
      valueSetVersionService.activate(valueSet.getId(), valueSetVersion.getVersion());
    }
    if (action.isRetire()) {
      valueSetVersionService.retire(valueSet.getId(), valueSetVersion.getVersion());
    }
    log.info("IMPORT FINISHED (" + (System.currentTimeMillis() - start) / 1000 + " sec)");
    return valueSet;
  }

  private void saveValueSet(ValueSet valueSet) {
    log.info("Saving value set");
    ValueSet existingValueSet = valueSetService.load(valueSet.getId());
    if (existingValueSet == null) {
      log.info("Value set {} does not exist, creating new", valueSet.getId());
      valueSetService.save(valueSet);
    }
  }

  private void saveValueSetVersion(ValueSetVersion valueSetVersion) {
    Optional<ValueSetVersion> existingVersion = valueSetVersionService.load(valueSetVersion.getValueSet(), valueSetVersion.getVersion());

    if (existingVersion.isPresent() && !existingVersion.get().getStatus().equals(PublicationStatus.draft)) {
      throw ApiError.TE104.toApiException(Map.of("version", valueSetVersion.getVersion()));
    }
    existingVersion.ifPresent(v -> valueSetVersionService.cancel(v.getId(), v.getValueSet()));
    log.info("Saving value set version {}", valueSetVersion.getVersion());
    valueSetVersionService.save(valueSetVersion);
    valueSetVersionRuleService.save(valueSetVersion.getRuleSet().getRules(), valueSetVersion.getValueSet(), valueSetVersion.getVersion());
  }

  private ValueSet prepare(ValueSet valueSet) {
    if (CollectionUtils.isNotEmpty(valueSet.getVersions()) && valueSet.getVersions().get(0) != null && valueSet.getVersions().get(0).getRuleSet() != null) {
      prepareRules(valueSet.getVersions().get(0).getRuleSet().getRules());
    }
    return valueSet;
  }

  private void prepareRules(List<ValueSetVersionRule> rules) {
    if (CollectionUtils.isEmpty(rules)) {
      return;
    }
    rules.forEach(r -> {
      if (StringUtils.isNotEmpty(r.getCodeSystemUri())) {
        CodeSystem codeSystem = codeSystemService.query(new CodeSystemQueryParams().setUri(r.getCodeSystemUri())).findFirst()
            .orElseThrow(() -> ApiError.TE110.toApiException(Map.of("cs", r.getCodeSystemUri())));
        CodeSystemVersion codeSystemVersion = codeSystemVersionService.loadLastVersion(codeSystem.getId());
        r.setCodeSystem(codeSystem.getId());
        r.setCodeSystemVersion(codeSystemVersion);
      }

      if (StringUtils.isNotEmpty(r.getValueSetUri())) {
        ValueSet valueSet = valueSetService.query(new ValueSetQueryParams().setUri(r.getValueSetUri())).findFirst()
            .orElseThrow(() -> ApiError.TE111.toApiException(Map.of("vs", r.getValueSetUri())));
        ValueSetVersion valueSetVersion = valueSetVersionService.loadLastVersion(valueSet.getId());
        r.setValueSet(valueSet.getId());
        r.setValueSetVersion(valueSetVersion);
      }
    });
  }
}
