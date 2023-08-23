package com.kodality.termx.terminology.valueset.concept;

import com.kodality.commons.util.DateUtil;
import com.kodality.termx.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.valueset.ValueSetVersionRepository;
import com.kodality.termx.terminology.valueset.snapshot.ValueSetSnapshotService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.ValueSetExternalExpandProvider;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetVersionConceptService {
  private final List<ValueSetExternalExpandProvider> externalExpandProviders;
  private final ValueSetVersionConceptRepository repository;
  private final ValueSetVersionRepository valueSetVersionRepository;
  private final ValueSetSnapshotService valueSetSnapshotService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private static final String DISPLAY = "display";
  private static final String DEPRECATION_DATE = "deprecationDate";
  private static final String INACTIVE = "inactive";
  private static final String STATUS = "status";
  private static final String RETIREMENT_DATE = "retirementDate";

  @Transactional
  public List<ValueSetVersionConcept> expand(String vs, String vsVersion) {
    ValueSetVersion version = getVersion(vs, vsVersion);
    if (version == null) {
      return new ArrayList<>();
    }
    List<ValueSetVersionConcept> expansion = expand(version, null);
    valueSetSnapshotService.createSnapshot(vs, version.getId(), expansion);
    return expansion;
  }

  @Transactional
  public List<ValueSetVersionConcept> expand(String vs, String vsVersion, String preferredLanguage) {
    ValueSetVersion version = getVersion(vs, vsVersion);
    if (version == null) {
      return new ArrayList<>();
    }
    List<ValueSetVersionConcept> expansion = expand(version, preferredLanguage);
    valueSetSnapshotService.createSnapshot(vs, version.getId(), expansion);
    return expansion;
  }

  public List<ValueSetVersionConcept> expand(ValueSetVersion version, String preferredLanguage) {
    if (version == null || version.getId() == null) {
      return new ArrayList<>();
    }

    if (PublicationStatus.active.equals(version.getStatus()) && version.getSnapshot() != null && version.getSnapshot().getExpansion() != null) {
      return version.getSnapshot().getExpansion();
    }

    List<ValueSetVersionConcept> expansion = internalExpand(version, preferredLanguage);
    ValueSetVersionRuleSet ruleSet = version.getRuleSet();
    for (ValueSetExternalExpandProvider provider : externalExpandProviders) {
      expansion.addAll(provider.expand(ruleSet, version, preferredLanguage));
    }
    return expansion;
  }

  private List<ValueSetVersionConcept> internalExpand(ValueSetVersion version, String preferredLanguage) {
    return decorate(repository.expand(version.getId()), version, preferredLanguage);
  }

  public List<ValueSetVersionConcept> decorate(List<ValueSetVersionConcept> concepts, ValueSetVersion version, String preferredLanguage) {
    List<String> supportedLanguages = Optional.ofNullable(version.getSupportedLanguages()).orElse(List.of());
    List<String> supportedProperties = version.getRuleSet() != null ? Optional.ofNullable(version.getRuleSet().getRules()).orElse(List.of()).stream()
        .filter(r -> r.getProperties() != null).flatMap(r -> r.getProperties().stream()).toList() : List.of();

    Map<String, List<ValueSetVersionConcept>> groupedConcepts = concepts.stream().collect(Collectors.groupingBy(c -> c.getConcept().getCode()));

    List<String> versionIds = concepts.stream().map(c -> c.getConcept().getConceptVersionId()).filter(Objects::nonNull).distinct().map(String::valueOf).toList();
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setIds(String.join(",", versionIds));
    params.limit(versionIds.size());
    List<CodeSystemEntityVersion> entityVersions = codeSystemEntityVersionService.query(params).getData();
    Map<String, List<CodeSystemEntityVersion>> groupedVersions = entityVersions.stream().collect(Collectors.groupingBy(CodeSystemEntityVersion::getCode));

    List<ValueSetVersionConcept> res = groupedConcepts.keySet().stream().map(code -> groupedConcepts.get(code).stream()
            .filter(ValueSetVersionConcept::isEnumerated).findFirst()
            .orElse(groupedConcepts.get(code).stream().findFirst().orElse(null)))
        .filter(Objects::nonNull)
        .peek(c -> {
          List<CodeSystemEntityVersion> versions = Optional.ofNullable(groupedVersions.get(c.getConcept().getCode())).orElse(new ArrayList<>());

          List<String> preferredLanguages = version.getPreferredLanguage() != null ?  List.of(version.getPreferredLanguage()) :
              versions.stream().flatMap(v -> v.getVersions().stream().map(CodeSystemVersionReference::getPreferredLanguage)).filter(Objects::nonNull).toList();
          List<String> csVersions = versions.stream().flatMap(v -> v.getVersions().stream().map(CodeSystemVersionReference::getVersion)).toList();
          c.getConcept().setCodeSystemVersions(csVersions);

          List<Designation> designations = versions.stream()
              .filter(v -> CollectionUtils.isNotEmpty(v.getDesignations()))
              .flatMap(v -> v.getDesignations().stream()).toList();
          if (c.getDisplay() == null || c.getDisplay().getName() == null) {
            List<Designation> displays = designations.stream().filter(d -> DISPLAY.equals(d.getDesignationType())).toList();
            c.setDisplay(displays.stream()
                .filter(d -> StringUtils.isNotEmpty(preferredLanguage) && d.getLanguage() != null && d.getLanguage().equals(preferredLanguage))
                .findFirst().orElse(displays.stream()
                    .filter(d -> StringUtils.isNotEmpty(preferredLanguage) && d.getLanguage() != null && d.getLanguage().startsWith(preferredLanguage))
                    .findFirst().orElse(displays.stream()
                        .filter(d -> CollectionUtils.isEmpty(preferredLanguages) ||
                            d.getLanguage() != null && preferredLanguages.stream().anyMatch(pl -> d.getLanguage().equals(pl)))
                        .findFirst().orElse(displays.stream()
                            .filter(d -> CollectionUtils.isEmpty(preferredLanguages) ||
                                d.getLanguage() != null && preferredLanguages.stream().anyMatch(pl -> d.getLanguage().startsWith(pl)))
                            .findFirst().orElse(null)))));
          }
          if (CollectionUtils.isEmpty(c.getAdditionalDesignations())) {
            c.setAdditionalDesignations(designations.stream()
                .filter(d -> CollectionUtils.isEmpty(supportedLanguages) || supportedLanguages.contains(d.getLanguage()))
                .filter(d -> c.getDisplay() == null || !d.getId().equals(c.getDisplay().getId())).toList());
          }
          c.setActive(calculatedActive(versions));
          c.setAssociations(versions.stream().filter(v -> CollectionUtils.isNotEmpty(v.getAssociations()))
              .flatMap(v -> v.getAssociations().stream()).collect(Collectors.toList()));
          c.setPropertyValues(versions.stream().filter(v -> CollectionUtils.isNotEmpty(v.getPropertyValues())).flatMap(v -> v.getPropertyValues().stream())
              .filter(p -> CollectionUtils.isEmpty(supportedProperties) || supportedProperties.contains(p.getEntityProperty())).collect(Collectors.toList()));
        }).collect(Collectors.toList());
    return res;
  }

  private boolean calculatedActive(List<CodeSystemEntityVersion> versions) {
    boolean inactive = versions.stream().anyMatch(v -> v.getPropertyValues() != null &&
        v.getPropertyValues().stream().anyMatch(pv -> pv.getEntityProperty().equals(INACTIVE) && (boolean) pv.getValue()));
    boolean status = versions.stream().anyMatch(v -> v.getPropertyValues() != null &&
        v.getPropertyValues().stream().anyMatch(pv -> pv.getEntityProperty().equals(STATUS) && List.of("deprecated", "retired").contains((String) pv.getValue())));
    boolean retired = dateIsAfter(versions, RETIREMENT_DATE);
    boolean deprecated = dateIsAfter(versions, DEPRECATION_DATE);
    boolean noActiveVersion = CollectionUtils.isNotEmpty(versions) && versions.stream().noneMatch(v -> PublicationStatus.active.equals(v.getStatus()));
    return !noActiveVersion && !status && !inactive && !retired && !deprecated;
  }

  private boolean dateIsAfter(List<CodeSystemEntityVersion> versions, String prop) {
    return versions.stream().anyMatch(v -> v.getPropertyValues() != null &&
        v.getPropertyValues().stream().filter(pv -> pv.getEntityProperty().equals(prop)).anyMatch(pv -> {
          OffsetDateTime date = pv.getValue() instanceof OffsetDateTime ? (OffsetDateTime) pv.getValue() : DateUtil.parseOffsetDateTime((String) pv.getValue());
          return date.isBefore(OffsetDateTime.now());
        }));
  }

  private ValueSetVersion getVersion(String vs, String vsVersion) {
    if (vs == null) {
      return null;
    }
    return Optional.ofNullable(vsVersion).map(v -> valueSetVersionRepository.load(vs, v)).orElse(valueSetVersionRepository.loadLastVersion(vs));
  }
}
