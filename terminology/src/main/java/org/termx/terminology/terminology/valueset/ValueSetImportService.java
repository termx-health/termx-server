package org.termx.terminology.terminology.valueset;

import com.kodality.commons.util.PipeUtil;
import org.termx.terminology.ApiError;
import org.termx.terminology.Privilege;
import org.termx.core.auth.SessionStore;
import org.termx.sys.spacepackage.PackageVersion;
import org.termx.sys.spacepackage.PackageVersion.PackageResource;
import org.termx.core.sys.spacepackage.resource.PackageResourceService;
import org.termx.core.sys.spacepackage.version.PackageVersionService;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService;
import org.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleService;
import org.termx.terminology.terminology.valueset.snapshot.ValueSetSnapshotService;
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemQueryParams;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.valueset.ValueSet;
import org.termx.ts.valueset.ValueSetImportAction;
import org.termx.ts.valueset.ValueSetQueryParams;
import org.termx.ts.valueset.ValueSetSnapshot;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
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
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final ValueSetSnapshotService valueSetSnapshotService;

  private final PackageVersionService packageVersionService;
  private final PackageResourceService packageResourceService;

  @Transactional
  public ValueSet importValueSet(ValueSet valueSet, ValueSetImportAction action) {
    SessionStore.require().checkPermitted(valueSet.getId(), Privilege.VS_EDIT);

    long start = System.currentTimeMillis();
    log.info("IMPORT STARTED : value set - {}", valueSet.getId());
    prepare(valueSet);

    saveValueSet(valueSet);
    ValueSetVersion valueSetVersion = valueSet.getVersions().getFirst();
    saveValueSetVersion(valueSetVersion, valueSetVersion.getSnapshot(), action.isCleanRun());

    if (action.isActivate()) {
      valueSetVersionService.activate(valueSet.getId(), valueSetVersion.getVersion());
    }
    if (action.isRetire()) {
      valueSetVersionService.retire(valueSet.getId(), valueSetVersion.getVersion());
    }
    if (StringUtils.isNotEmpty(action.getSpaceToAdd())) {
      addToSpace(valueSet.getId(), action.getSpaceToAdd());
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
    } else {
      log.info("Updating value set {}", valueSet.getId());
      valueSetService.save(valueSet);
    }
  }

  private void saveValueSetVersion(ValueSetVersion valueSetVersion, ValueSetSnapshot snapshot, boolean cleanRun) {
    Optional<ValueSetVersion> existingVersion = valueSetVersionService.load(valueSetVersion.getValueSet(), valueSetVersion.getVersion());

    if (existingVersion.isPresent() && !existingVersion.get().getStatus().equals(PublicationStatus.draft) && !cleanRun) {
      throw ApiError.TE104.toApiException(Map.of("version", valueSetVersion.getVersion()));
    }
    existingVersion.ifPresent(v -> valueSetVersionService.cancel(v.getId()));
    log.info("Saving value set version {}", valueSetVersion.getVersion());
    valueSetVersionService.save(valueSetVersion);
    valueSetVersionRuleService.save(valueSetVersion.getRuleSet().getRules(), valueSetVersion.getValueSet(), valueSetVersion.getVersion());
    if (snapshot == null) {
      valueSetVersionConceptService.expand(valueSetVersion.getValueSet(), valueSetVersion.getVersion());
    } else {
      valueSetSnapshotService.createSnapshot(valueSetVersion.getValueSet(), valueSetVersion.getId(), snapshot.getExpansion());
    }
  }

  private ValueSet prepare(ValueSet valueSet) {
    if (CollectionUtils.isNotEmpty(valueSet.getVersions()) && valueSet.getVersions().getFirst() != null && valueSet.getVersions().getFirst().getRuleSet() != null) {
      prepareRules(valueSet.getVersions().getFirst().getRuleSet().getRules());
    }
    if (CollectionUtils.isNotEmpty(valueSet.getVersions()) && valueSet.getVersions().getFirst() != null && valueSet.getVersions().getFirst().getSnapshot() != null) {
      prepareSnapshot(valueSet.getVersions().getFirst().getSnapshot().getExpansion());
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

        CodeSystemVersion codeSystemVersion = null;
        if (r.getCodeSystemVersion() != null && r.getCodeSystemVersion().getId() != null) {
          codeSystemVersion = codeSystemVersionService.load(r.getCodeSystemVersion().getId());
        } else if (r.getCodeSystemVersion() != null && r.getCodeSystemVersion().getVersion() != null) {
          codeSystemVersion = codeSystemVersionService.load(codeSystem.getId(), r.getCodeSystemVersion().getVersion()).orElse(null);
        }
        if (codeSystemVersion == null) {
          codeSystemVersion = codeSystemVersionService.loadLastVersion(codeSystem.getId());
        }
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

  private void prepareSnapshot(List<ValueSetVersionConcept> concepts) {
    if (CollectionUtils.isEmpty(concepts)) {
      return;
    }
    String uri = concepts.stream().filter(c -> c.getConcept().getCodeSystemUri() != null && c.getConcept().getCodeSystem() == null)
        .map(c -> c.getConcept().getCodeSystemUri()).distinct().collect(Collectors.joining(","));
    Map<String, CodeSystem> codeSystems = codeSystemService.query(new CodeSystemQueryParams().setUri(uri).limit(uri.split(",").length)).getData().stream().collect(Collectors.toMap(CodeSystem::getUri, cs -> cs));
    concepts.stream().filter(c -> c.getConcept().getCodeSystemUri() != null && c.getConcept().getCodeSystem() == null)
        .forEach(c -> c.getConcept().setCodeSystem(codeSystems.getOrDefault(c.getConcept().getCodeSystemUri(), new CodeSystem()).getId()));
  }

  private void addToSpace(String valueSetId, String spaceToAdd) {
    String[] spaceAndPackage = PipeUtil.parsePipe(spaceToAdd);
    PackageVersion packageVersion = packageVersionService.loadLastVersion(spaceAndPackage[0], spaceAndPackage[1]);
    if (packageVersion == null) {
      throw ApiError.TE112.toApiException(Map.of("space", spaceAndPackage[0], "package", spaceAndPackage[1]));
    }

    List<PackageResource> resources = packageVersion.getResources() == null ? List.of() : packageVersion.getResources();
    boolean exists = resources.stream().anyMatch(r -> r.getResourceType().equals("value-set") && r.getResourceId().equals(valueSetId));
    if (!exists) {
      PackageResource pr = new PackageResource();
      pr.setResourceType("value-set");
      pr.setResourceId(valueSetId);
      packageResourceService.save(packageVersion.getId(), pr);
    }
  }
}
