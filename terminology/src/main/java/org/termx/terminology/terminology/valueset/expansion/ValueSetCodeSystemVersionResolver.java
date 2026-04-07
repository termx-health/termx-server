package org.termx.terminology.terminology.valueset.expansion;

import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.CodeSystemVersionReference;
import org.termx.ts.valueset.ValueSetSnapshotDependency;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;

@Singleton
@RequiredArgsConstructor
public class ValueSetCodeSystemVersionResolver {
  private final CodeSystemVersionService codeSystemVersionService;

  public CodeSystemVersion resolve(String codeSystem, CodeSystemVersionReference requestedVersion) {
    if (codeSystem == null) {
      return null;
    }
    if (requestedVersion != null && requestedVersion.getVersion() != null) {
      return codeSystemVersionService.load(codeSystem, requestedVersion.getVersion()).orElse(null);
    }
    if (requestedVersion != null && requestedVersion.getId() != null) {
      return codeSystemVersionService.load(requestedVersion.getId());
    }
    return codeSystemVersionService.loadLastVersion(codeSystem);
  }

  public List<ValueSetSnapshotDependency> collectDependencies(ValueSetVersion version) {
    if (version == null || version.getRuleSet() == null || CollectionUtils.isEmpty(version.getRuleSet().getRules())) {
      return List.of();
    }
    Map<String, ValueSetSnapshotDependency> dependencies = new LinkedHashMap<>();
    for (ValueSetVersionRule rule : version.getRuleSet().getRules()) {
      if (rule == null || rule.getCodeSystem() == null) {
        continue;
      }
      CodeSystemVersion resolvedVersion = resolve(rule.getCodeSystem(), rule.getCodeSystemVersion());
      String dependencyKey = dependencyKey(rule.getCodeSystem(), rule.getId(), resolvedVersion);
      dependencies.put(dependencyKey, new ValueSetSnapshotDependency()
          .setRuleId(rule.getId())
          .setCodeSystem(rule.getCodeSystem())
          .setDynamic(isDynamic(rule))
          .setCodeSystemVersion(copyReference(resolvedVersion)));
    }
    return new ArrayList<>(dependencies.values());
  }

  public boolean isDynamic(ValueSetVersionRule rule) {
    return rule != null &&
        rule.getCodeSystem() != null &&
        (rule.getCodeSystemVersion() == null ||
            (rule.getCodeSystemVersion().getId() == null && rule.getCodeSystemVersion().getVersion() == null));
  }

  public CodeSystemVersionReference copyReference(CodeSystemVersion version) {
    if (version == null) {
      return null;
    }
    return new CodeSystemVersionReference()
        .setId(version.getId())
        .setVersion(version.getVersion())
        .setUri(version.getUri())
        .setStatus(version.getStatus())
        .setPreferredLanguage(version.getPreferredLanguage())
        .setReleaseDate(version.getReleaseDate())
        .setBaseCodeSystemVersion(copyReference(version.getBaseCodeSystemVersion()));
  }

  public CodeSystemVersionReference copyReference(CodeSystemVersionReference version) {
    if (version == null) {
      return null;
    }
    return new CodeSystemVersionReference()
        .setId(version.getId())
        .setVersion(version.getVersion())
        .setUri(version.getUri())
        .setStatus(version.getStatus())
        .setPreferredLanguage(version.getPreferredLanguage())
        .setReleaseDate(version.getReleaseDate())
        .setBaseCodeSystemVersion(copyReference(version.getBaseCodeSystemVersion()));
  }

  private String dependencyKey(String codeSystem, Long ruleId, CodeSystemVersion version) {
    return String.join("|",
        Objects.toString(codeSystem, ""),
        Objects.toString(ruleId, ""),
        Objects.toString(version == null ? null : version.getId(), ""));
  }
}
