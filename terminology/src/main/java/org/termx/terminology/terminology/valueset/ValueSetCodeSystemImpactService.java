package org.termx.terminology.terminology.valueset;

import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.termx.terminology.terminology.valueset.expansion.ValueSetCodeSystemVersionResolver;
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService;
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystemArtifactImpact;
import org.termx.ts.codesystem.CodeSystemVersionReference;
import org.termx.ts.valueset.ValueSetSnapshot;
import org.termx.ts.valueset.ValueSetSnapshotDependency;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionQueryParams;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;

@Singleton
@RequiredArgsConstructor
public class ValueSetCodeSystemImpactService {
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final ValueSetCodeSystemVersionResolver codeSystemVersionResolver;

  public List<CodeSystemArtifactImpact> findValueSetImpacts(String codeSystem) {
    List<ValueSetVersion> versions = valueSetVersionService.query(new ValueSetVersionQueryParams().setCodeSystem(codeSystem).all()).getData();
    List<CodeSystemArtifactImpact> impacts = new ArrayList<>();
    for (ValueSetVersion version : versions) {
      List<ValueSetVersionRule> rules = Optional.ofNullable(version.getRuleSet()).map(rs -> rs.getRules()).orElse(List.of()).stream()
          .filter(rule -> rule != null && Objects.equals(codeSystem, rule.getCodeSystem()))
          .toList();
      if (CollectionUtils.isEmpty(rules)) {
        continue;
      }
      impacts.add(aggregateImpact(version, rules));
    }
    return impacts;
  }

  public void refreshDynamicValueSets(String codeSystem) {
    List<ValueSetVersion> versions = valueSetVersionService.query(new ValueSetVersionQueryParams().setCodeSystem(codeSystem).all()).getData();
    versions.stream()
        .filter(v -> List.of(PublicationStatus.active, PublicationStatus.draft).contains(v.getStatus()))
        .filter(v -> Optional.ofNullable(v.getRuleSet()).map(rs -> rs.getRules()).orElse(List.of()).stream()
            .anyMatch(rule -> Objects.equals(codeSystem, rule.getCodeSystem()) && codeSystemVersionResolver.isDynamic(rule)))
        .forEach(v -> valueSetVersionConceptService.expand(v.getValueSet(), v.getVersion()));
  }

  private CodeSystemArtifactImpact toImpact(ValueSetVersion version, ValueSetVersionRule rule) {
    CodeSystemVersionReference currentVersion = codeSystemVersionResolver.copyReference(codeSystemVersionResolver.resolve(rule.getCodeSystem(), null));
    boolean dynamic = codeSystemVersionResolver.isDynamic(rule);
    CodeSystemVersionReference resolvedVersion = dynamic
        ? snapshotDependency(version.getSnapshot(), rule.getCodeSystem())
        : codeSystemVersionResolver.copyReference(codeSystemVersionResolver.resolve(rule.getCodeSystem(), rule.getCodeSystemVersion()));
    boolean affected = currentVersion != null && !Objects.equals(currentVersion.getId(), resolvedVersion == null ? null : resolvedVersion.getId());

    return new CodeSystemArtifactImpact()
        .setArtifactType("ValueSet")
        .setArtifactId(version.getValueSet())
        .setArtifactVersion(version.getVersion())
        .setDynamic(dynamic)
        .setAffected(affected)
        .setReason(reason(dynamic, resolvedVersion, currentVersion))
        .setSnapshotCreatedAt(version.getSnapshot() == null ? null : version.getSnapshot().getCreatedAt())
        .setResolvedCodeSystemVersion(resolvedVersion)
        .setCurrentCodeSystemVersion(currentVersion);
  }

  private CodeSystemArtifactImpact aggregateImpact(ValueSetVersion version, List<ValueSetVersionRule> rules) {
    List<CodeSystemArtifactImpact> ruleImpacts = rules.stream().map(rule -> toImpact(version, rule)).toList();
    return ruleImpacts.stream()
        .max((left, right) -> {
          int severityCompare = Integer.compare(severity(left), severity(right));
          if (severityCompare != 0) {
            return severityCompare;
          }
          return Boolean.compare(left.isDynamic(), right.isDynamic());
        })
        .orElseThrow();
  }

  private int severity(CodeSystemArtifactImpact impact) {
    if (impact.isAffected()) {
      return 1;
    }
    if (impact.getResolvedCodeSystemVersion() == null) {
      return 0;
    }
    return -1;
  }

  private CodeSystemVersionReference snapshotDependency(ValueSetSnapshot snapshot, String codeSystem) {
    return Optional.ofNullable(snapshot).map(ValueSetSnapshot::getDependencies).orElse(List.of()).stream()
        .filter(ValueSetSnapshotDependency::isDynamic)
        .filter(d -> Objects.equals(codeSystem, d.getCodeSystem()))
        .map(ValueSetSnapshotDependency::getCodeSystemVersion)
        .findFirst()
        .orElse(null);
  }

  private String reason(boolean dynamic, CodeSystemVersionReference resolvedVersion, CodeSystemVersionReference currentVersion) {
    if (currentVersion == null) {
      return "No active or draft code system version available";
    }
    if (resolvedVersion == null) {
      return dynamic ? "Snapshot was built before dynamic source tracking was stored" : "Referenced code system version is missing";
    }
    if (Objects.equals(resolvedVersion.getId(), currentVersion.getId())) {
      return dynamic ? "Latest snapshot is current" : "Referenced version matches current latest";
    }
    return dynamic ? "Latest source version changed after snapshot generation" : "A newer code system version is available";
  }
}
