package com.kodality.termx.fhir.valueset;

import com.kodality.termx.ApiError;
import com.kodality.termx.http.BinaryHttpClient;
import com.kodality.termx.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.codesystem.CodeSystemVersionService;
import com.kodality.termx.terminology.valueset.ValueSetService;
import com.kodality.termx.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.terminology.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.ResourceType;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
public class ValueSetFhirImportService {
  private final ValueSetService valueSetService;
  private final CodeSystemService codeSystemService;
  private final ValueSetVersionService valueSetVersionService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public ValueSet importValueSet(com.kodality.zmei.fhir.resource.terminology.ValueSet fhirValueSet, boolean activateVersion) {
    ValueSet valueSet = prepare(ValueSetFhirMapper.fromFhirValueSet(fhirValueSet));
    ValueSetVersion valueSetVersion = prepareValueSetAndVersion(valueSet);
    if (activateVersion) {
      valueSetVersionService.activate(valueSet.getId(), valueSetVersion.getVersion());
    }
    return valueSet;
  }

  public void importValueSetFromUrl(String url, String id) {
    String resource = getResource(url);
    importValueSet(resource, id);
  }

  public void importValueSet(String resource, String id) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet fhir = FhirMapper.fromJson(resource, com.kodality.zmei.fhir.resource.terminology.ValueSet.class);
    if (!ResourceType.valueSet.equals(fhir.getResourceType())) {
      throw ApiError.TE107.toApiException();
    }
    fhir.setId(id);
    importValueSet(fhir, PublicationStatus.active.equals(fhir.getStatus()));
  }

  private String getResource(String url) {
    log.info("Loading fhir value set from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.UTF_8);
  }

  private ValueSet prepare(ValueSet valueSet) {
    if (CollectionUtils.isNotEmpty(valueSet.getVersions()) && valueSet.getVersions().get(0) != null && valueSet.getVersions().get(0).getRuleSet() != null) {
      prepareRules(valueSet);
    }
    return valueSet;
  }

  private void prepareRules(ValueSet valueSet) {
    List<ValueSetVersionRule> rules = valueSet.getVersions().get(0).getRuleSet().getRules();
    OffsetDateTime lockedDate = valueSet.getVersions().get(0).getRuleSet().getLockedDate();

    if (CollectionUtils.isEmpty(rules)) {
      return;
    }
    rules.forEach(r -> {
      prepareRuleValueSet(r);
      prepareRuleCodeSystem(r, lockedDate);
    });
  }

  private void prepareRuleValueSet(ValueSetVersionRule r) {
    if (StringUtils.isNotEmpty(r.getValueSet())) {
      valueSetVersionService.query(new ValueSetVersionQueryParams()
          .setValueSetUri(r.getValueSet())
          .setReleaseDateLe(LocalDate.now())
          .setExpirationDateGe(LocalDate.now())
      ).findFirst().ifPresent(version -> {
        r.setValueSet(version.getValueSet());
        r.setValueSetVersion(version);
      });
    }
  }

  private void prepareRuleCodeSystem(ValueSetVersionRule r, OffsetDateTime lockedDate) {
    if (StringUtils.isNotEmpty(r.getCodeSystem())) {
      codeSystemService.query(new CodeSystemQueryParams().setUri(r.getCodeSystem())).findFirst().ifPresent(cs -> r.setCodeSystem(cs.getId()));
      if (lockedDate == null) {
        r.setCodeSystemVersion(codeSystemVersionService.query(new CodeSystemVersionQueryParams()
            .setCodeSystem(r.getCodeSystem())
            .setReleaseDateLe(LocalDate.now())
            .setExpirationDateGe(LocalDate.now())).findFirst().orElse(null));
      }
    }
  }

  private ValueSetVersion prepareValueSetAndVersion(ValueSet valueSet) {
    log.info("Checking, the value set and version exists");
    ValueSet existingValueSet = valueSetService.load(valueSet.getId());
    if (existingValueSet == null) {
      log.info("Value set {} does not exist, creating new", valueSet.getId());
      valueSetService.save(valueSet);
    }

    ValueSetVersion version = valueSet.getVersions().get(0);
    Optional<ValueSetVersion> existingVersion = valueSetVersionService.load(valueSet.getId(), version.getVersion());
    if (existingVersion.isPresent() && !existingVersion.get().getStatus().equals(PublicationStatus.draft)) {
      throw ApiError.TE104.toApiException(Map.of("version", version.getVersion()));
    }
    existingVersion.ifPresent(v -> valueSetVersionService.cancel(v.getId(), valueSet.getId()));
    log.info("Saving value set version {}", version.getVersion());
    valueSetVersionService.save(version);
    valueSetVersionRuleService.save(version.getRuleSet().getRules(), valueSet.getId(), version.getVersion());
    return version;
  }
}
