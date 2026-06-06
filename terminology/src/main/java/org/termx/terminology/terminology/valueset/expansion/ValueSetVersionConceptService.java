package org.termx.terminology.terminology.valueset.expansion;

import com.kodality.commons.util.DateUtil;
import org.termx.core.auth.SessionStore;
import org.termx.terminology.Privilege;
import org.termx.terminology.terminology.codesystem.concept.ConceptUtil;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import org.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService;
import org.termx.terminology.terminology.valueset.version.ValueSetVersionRepository;
import org.termx.terminology.terminology.valueset.snapshot.ValueSetSnapshotService;
import org.termx.ts.PublicationStatus;
import org.termx.core.ts.ValueSetExternalExpandProvider;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import org.termx.ts.codesystem.CodeSystemVersionReference;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.EntityProperty;
import org.termx.ts.codesystem.EntityPropertyQueryParams;
import org.termx.ts.codesystem.EntityPropertyType;
import org.termx.ts.codesystem.EntityPropertyValue;
import org.termx.ts.property.PropertyReference;
import org.termx.ts.valueset.ValueSetSnapshot;
import org.termx.ts.valueset.ValueSetVersionReference;
import org.termx.ts.valueset.ValueSetSnapshotDependency;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionRuleSet;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Singleton;
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
  private final EntityPropertyService entityPropertyService;
  private final ValueSetCodeSystemVersionResolver codeSystemVersionResolver;

  private static final String DEPRECATION_DATE = "deprecationDate";
  private static final String INACTIVE = "inactive";
  private static final String STATUS = "status";
  private static final String RETIREMENT_DATE = "retirementDate";
  private static final String SNOMED_URI = "http://snomed.info/sct";
  @Transactional
  public List<ValueSetVersionConcept> expand(String vs, String vsVersion) {
    ValueSetVersion version = getVersion(vs, vsVersion);
    if (version == null) {
      return new ArrayList<>();
    }
    List<ValueSetVersionConcept> expansion = expand(version, null);
    valueSetSnapshotService.createSnapshot(vs, version.getId(), expansion, resolveDependencies(version));
    return expansion;
  }

  @Transactional
  public ValueSetSnapshot expand(String vs, String vsVersion, String preferredLanguage) {
    return expand(vs, vsVersion, preferredLanguage, false);
  }

  @Transactional
  public ValueSetSnapshot expand(String vs, String vsVersion, String preferredLanguage, boolean includeDesignations) {
    ValueSetVersion version = getVersion(vs, vsVersion);
    if (version == null) {
      return null;
    }
    // "Default rendering" = no caller-specific language/designations. Only this view is stored as the
    // canonical snapshot; a request for a specific displayLanguage is a read-only view that must
    // never overwrite the stored snapshot.
    boolean draft = PublicationStatus.draft.equals(version.getStatus());
    boolean defaultView = !includeDesignations && StringUtils.isEmpty(preferredLanguage);
    ValueSetSnapshot snapshot = version.getSnapshot();
    boolean snapshotUsable = snapshot != null && snapshot.getExpansion() != null && isSnapshotCurrent(version, snapshot);

    // (a) Published versions (active or retired) are frozen — serve from the stored snapshot; only a
    // draft is (re)expanded on demand while authoring.
    if (!draft && snapshotUsable) {
      if (defaultView) {
        return snapshot;
      }
      // (b)/(f) Render the requested displayLanguage WITHOUT recomputing concepts or rewriting the snapshot.
      return renderLanguage(version, snapshot, preferredLanguage);
    }

    List<ValueSetVersionConcept> expansion = expand(version, preferredLanguage, includeDesignations);

    // (c) Persist (overwrite) the canonical snapshot only for the default rendering AND only when the
    // caller may write the value set. A public / read-only FHIR $expand never overwrites the stored
    // snapshot — previously every $expand with a displayLanguage, or against a retired version,
    // recreated it, replacing the frozen content and bumping its date on each read.
    if (defaultView && canPersistSnapshot(vs)) {
      return valueSetSnapshotService.createSnapshot(vs, version.getId(), expansion, resolveDependencies(version));
    }
    return transientSnapshot(vs, version.getId(), expansion);
  }

  /**
   * Render a stored snapshot for a specific displayLanguage without recomputing the concepts or
   * rewriting the snapshot. (f) When the language is one the value set version declares
   * ({@code supportedLanguages}), the snapshot already carries its designations, so the display is
   * re-picked from the snapshot with no DB access. (b) Otherwise only the display designations for
   * that language are loaded (no rule re-expansion), overlaid onto the existing concepts.
   */
  private ValueSetSnapshot renderLanguage(ValueSetVersion version, ValueSetSnapshot snapshot, String displayLanguage) {
    List<ValueSetVersionConcept> source = Optional.ofNullable(snapshot.getExpansion()).orElse(List.of());
    List<String> supportedLanguages = Optional.ofNullable(version.getSupportedLanguages()).orElse(List.of());
    List<String> preferredLanguages = version.getPreferredLanguage() != null ? List.of(version.getPreferredLanguage()) : List.of();
    Map<Long, Designation> loaded = supportedLanguages.contains(displayLanguage) ? Map.of() : loadDisplayDesignations(source, displayLanguage);

    List<ValueSetVersionConcept> rendered = source.stream().map(c -> {
      List<Designation> all = new ArrayList<>();
      if (c.getDisplay() != null) {
        all.add(c.getDisplay());
      }
      if (c.getAdditionalDesignations() != null) {
        all.addAll(c.getAdditionalDesignations());
      }
      Long conceptVersionId = c.getConcept() == null ? null : c.getConcept().getConceptVersionId();
      Designation extra = conceptVersionId == null ? null : loaded.get(conceptVersionId);
      if (extra != null) {
        all.add(extra);
      }
      Designation display = ConceptUtil.getDisplay(all, displayLanguage, preferredLanguages);
      if (display == null) {
        return c;
      }
      List<Designation> additional = extra == null ? c.getAdditionalDesignations()
          : Stream.concat(Optional.ofNullable(c.getAdditionalDesignations()).orElse(List.of()).stream(), Stream.of(extra)).toList();
      return withDisplay(c, display, additional);
    }).toList();
    return transientSnapshot(snapshot.getValueSet(), version.getId(), rendered);
  }

  /** Load ONLY the display designations for {@code language} for the given concepts (no rule expansion). */
  private Map<Long, Designation> loadDisplayDesignations(List<ValueSetVersionConcept> concepts, String language) {
    List<String> versionIds = concepts.stream()
        .map(c -> c.getConcept() == null ? null : c.getConcept().getConceptVersionId())
        .filter(Objects::nonNull).distinct().map(String::valueOf).toList();
    if (versionIds.isEmpty()) {
      return Map.of();
    }
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setIds(String.join(",", versionIds));
    params.limit(versionIds.size());
    Map<Long, Designation> result = new HashMap<>();
    for (CodeSystemEntityVersion v : codeSystemEntityVersionService.query(params).getData()) {
      if (v.getId() == null || v.getDesignations() == null) {
        continue;
      }
      v.getDesignations().stream()
          .filter(d -> "display".equals(d.getDesignationType()) && !PublicationStatus.retired.equals(d.getStatus()))
          .filter(d -> d.getLanguage() != null && (d.getLanguage().equals(language) || d.getLanguage().startsWith(language)))
          .min(Comparator.comparing(Designation::isPreferred).reversed())
          .ifPresent(d -> result.putIfAbsent(v.getId(), d));
    }
    return result;
  }

  /** Shallow copy of a snapshot concept overriding only its display (and optionally additionalDesignations). */
  private static ValueSetVersionConcept withDisplay(ValueSetVersionConcept c, Designation display, List<Designation> additionalDesignations) {
    return new ValueSetVersionConcept()
        .setId(c.getId())
        .setConcept(c.getConcept())
        .setDisplay(display)
        .setAdditionalDesignations(additionalDesignations)
        .setOrderNumber(c.getOrderNumber())
        .setEnumerated(c.isEnumerated())
        .setActive(c.isActive())
        .setStatus(c.getStatus())
        .setAssociations(c.getAssociations())
        .setPropertyValues(c.getPropertyValues());
  }

  private static ValueSetSnapshot transientSnapshot(String vs, Long versionId, List<ValueSetVersionConcept> expansion) {
    return new ValueSetSnapshot()
        .setValueSet(vs)
        .setValueSetVersion(new ValueSetVersionReference().setId(versionId))
        .setExpansion(expansion)
        .setConceptsTotal(expansion.size());
  }

  /** A snapshot may be (over)written only by a caller with write/maintain rights on the value set. */
  private static boolean canPersistSnapshot(String valueSet) {
    return SessionStore.get()
        .map(s -> s.hasPrivilege(valueSet + "." + Privilege.VS_WRITE) || s.hasPrivilege(valueSet + "." + Privilege.VS_MAINTAIN))
        .orElse(false);
  }

  public List<ValueSetVersionConcept> expand(ValueSetVersion version, String preferredLanguage) {
    return expand(version, preferredLanguage, false);
  }

  public List<ValueSetVersionConcept> expand(ValueSetVersion version, String preferredLanguage, boolean includeDesignations) {
    if (version == null || version.getId() == null) {
      return new ArrayList<>();
    }

    if (!includeDesignations &&
        StringUtils.isEmpty(preferredLanguage) &&
        !PublicationStatus.draft.equals(version.getStatus()) &&
        version.getSnapshot() != null && version.getSnapshot().getExpansion() != null &&
        isSnapshotCurrent(version, version.getSnapshot())) {
      return version.getSnapshot().getExpansion();
    }

    List<ValueSetVersionConcept> expansion = internalExpand(version, preferredLanguage).stream()
        .filter(e -> e.isEnumerated() || e.getConcept().getConceptVersionId() != null)
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    ValueSetVersionRuleSet ruleSet = version.getRuleSet();
    List<ValueSetVersionConcept> externalExpansion = new ArrayList<>();
    for (ValueSetExternalExpandProvider provider : externalExpandProviders) {
      externalExpansion.addAll(provider.expand(ruleSet, version, preferredLanguage));
    }
    expansion.addAll(externalExpansion);
    if (!ruleSet.isInactive()) {
      return expansion.stream().filter(ValueSetVersionConcept::isActive).toList();
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
    Map<String, EntityProperty> properties = entityPropertyService.query(new EntityPropertyQueryParams()
            .setCodeSystem(version.getRuleSet().getRules().stream().map(ValueSetVersionRule::getCodeSystem).collect(Collectors.joining(","))))
        .getData().stream().filter(p -> CollectionUtils.isEmpty(supportedProperties) || supportedProperties.contains(p.getName()))
        .filter(distinctByKey(PropertyReference::getName))
        .collect(Collectors.toMap(PropertyReference::getName, p -> p));

    Map<String, List<ValueSetVersionConcept>> groupedConcepts = concepts.stream().collect(Collectors.groupingBy(c -> c.getConcept().getCodeSystem() + c.getConcept().getCode()));

    List<String> versionIds = concepts.stream().map(c -> c.getConcept().getConceptVersionId()).filter(Objects::nonNull).distinct().map(String::valueOf).toList();
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setIds(String.join(",", versionIds));
    params.limit(versionIds.size());
    List<CodeSystemEntityVersion> entityVersions = codeSystemEntityVersionService.query(params).getData();
    Map<String, List<CodeSystemEntityVersion>> groupedVersions = entityVersions.stream().collect(Collectors.groupingBy(v -> v.getCodeSystem() + v.getCode()));

    List<ValueSetVersionConcept> res = groupedConcepts.keySet().stream().map(key -> groupedConcepts.get(key).stream()
            .filter(ValueSetVersionConcept::isEnumerated).findFirst()
            .orElse(groupedConcepts.get(key).stream().findFirst().orElse(null)))
        .filter(Objects::nonNull)
        .peek(c -> {
          List<CodeSystemEntityVersion> versions = Optional.ofNullable(groupedVersions.get(c.getConcept().getCodeSystem() + c.getConcept().getCode())).orElse(new ArrayList<>());

          List<String> preferredLanguages = version.getPreferredLanguage() != null ? List.of(version.getPreferredLanguage()) :
              versions.stream().flatMap(v -> Optional.ofNullable(v.getVersions()).orElse(List.of()).stream().map(CodeSystemVersionReference::getPreferredLanguage)).filter(Objects::nonNull).toList();
          List<String> csVersions = versions.stream().flatMap(v -> Optional.ofNullable(v.getVersions()).orElse(List.of()).stream().map(CodeSystemVersionReference::getVersion)).toList();
          c.getConcept().setCodeSystemVersions(csVersions);

          List<Designation> designations = versions.stream()
              .filter(v -> CollectionUtils.isNotEmpty(v.getDesignations()))
              .flatMap(v -> v.getDesignations().stream())
              .filter(d -> !PublicationStatus.retired.equals(d.getStatus())).toList();
          if (c.getDisplay() == null || StringUtils.isEmpty(c.getDisplay().getName())) {
            c.setDisplay(ConceptUtil.getDisplay(designations, preferredLanguage, preferredLanguages));
          }
          if (CollectionUtils.isEmpty(c.getAdditionalDesignations())) {
            c.setAdditionalDesignations(designations.stream()
                .filter(d -> CollectionUtils.isEmpty(supportedLanguages) || supportedLanguages.contains(d.getLanguage()))
                .filter(d -> c.getDisplay() == null || d != c.getDisplay())
                .filter(d -> !SNOMED_URI.equals(c.getConcept().getBaseCodeSystemUri()) || !"display".equals(d.getDesignationType()))
                .filter(d -> !d.isSupplement() || designations.stream().noneMatch(d1 -> d1.getDesignationType().equals(d.getDesignationType()) && d1 != d)).toList());
          }
          c.setActive(calculatedActive(versions));
          c.setStatus(versions.stream().findFirst().map(CodeSystemEntityVersion::getStatus).orElse(PublicationStatus.active));
          c.setAssociations(versions.stream().filter(v -> CollectionUtils.isNotEmpty(v.getAssociations()))
              .flatMap(v -> v.getAssociations().stream()).toList());
          // Optimization of simple EntityPropertyValue (versions excluded)
          c.setPropertyValues(versions.stream()
                    .filter(v -> CollectionUtils.isNotEmpty(v.getPropertyValues()))
                    .flatMap(v -> v.getPropertyValues().stream())
                    .filter(p -> properties.containsKey(p.getEntityProperty()))
                    .peek(p -> {
                        Object val = p.getValue();
                        if (val instanceof Map<?, ?> map) {
                            if (map.containsKey("id")) {
                                // new map only with code and system
                                Map<String, Object> newVal = new LinkedHashMap<>();
                                newVal.put("code", map.get("code"));
                                newVal.put("system", map.get("codeSystem"));
                                newVal.put("codeSystem", map.get("codeSystem"));
                                p.setValue(newVal);
                            }
                        }
                    })
                    // merge duplicity
                    .filter(distinctByKey(p -> Arrays.asList(
                            p.getValue(),
                            p.getEntityPropertyId(),
                            p.getCodeSystemEntityVersionId(),
                            p.getEntityProperty(),
                            p.getEntityPropertyType()
                    )))
                    .toList());
          if (properties.containsKey("modifiedAt")) {
            c.getPropertyValues().add(new EntityPropertyValue()
                .setValue(versions.stream().findFirst().map(CodeSystemEntityVersion::getSysModifiedAt).orElse(null))
                .setEntityProperty("modifiedAt")
                .setEntityPropertyType(properties.get("modifiedAt").getType()));
          }
          if (properties.containsKey("modifiedBy")) {
            c.getPropertyValues().add(new EntityPropertyValue()
                .setValue(versions.stream().findFirst().map(CodeSystemEntityVersion::getSysModifiedBy).orElse(null))
                .setEntityProperty("modifiedBy")
                .setEntityPropertyType(properties.get("modifiedBy").getType()));
          }
        }).toList();
    return res;
  }

  private boolean calculatedActive(List<CodeSystemEntityVersion> versions) {
    boolean inactive = versions.stream().anyMatch(v -> v.getPropertyValues() != null &&
        v.getPropertyValues().stream()
            .anyMatch(pv -> pv.getEntityProperty().equals(INACTIVE) && EntityPropertyType.bool.equals(pv.getEntityPropertyType()) && (boolean) pv.getValue()));
    boolean status = versions.stream().anyMatch(v -> v.getPropertyValues() != null &&
        v.getPropertyValues().stream().anyMatch(pv -> pv.getEntityProperty().equals(STATUS) && EntityPropertyType.string.equals(pv.getEntityPropertyType()) &&
            List.of("deprecated", "retired").contains((String) pv.getValue())));
    boolean retired = dateIsAfter(versions, RETIREMENT_DATE);
    boolean deprecated = dateIsAfter(versions, DEPRECATION_DATE);
    boolean noActiveVersion = CollectionUtils.isNotEmpty(versions) && versions.stream().noneMatch(v -> PublicationStatus.active.equals(v.getStatus()));
    return !noActiveVersion && !status && !inactive && !retired && !deprecated;
  }

  private boolean dateIsAfter(List<CodeSystemEntityVersion> versions, String prop) {
    return versions.stream().anyMatch(v -> v.getPropertyValues() != null &&
        v.getPropertyValues().stream().filter(pv -> pv.getEntityProperty().equals(prop)).anyMatch(pv -> {
          OffsetDateTime date = pv.getValue() instanceof OffsetDateTime odt ? odt : DateUtil.parseOffsetDateTime((String) pv.getValue());
          return date.isBefore(OffsetDateTime.now());
        }));
  }

  private ValueSetVersion getVersion(String vs, String vsVersion) {
    if (vs == null) {
      return null;
    }
    return Optional.ofNullable(vsVersion).map(v -> valueSetVersionRepository.load(vs, v)).orElse(valueSetVersionRepository.loadLastVersion(vs));
  }

  private List<ValueSetSnapshotDependency> resolveDependencies(ValueSetVersion version) {
    return codeSystemVersionResolver.collectDependencies(version);
  }

  private boolean isSnapshotCurrent(ValueSetVersion version, ValueSetSnapshot snapshot) {
    List<ValueSetVersionRule> rules = Optional.ofNullable(version.getRuleSet()).map(ValueSetVersionRuleSet::getRules).orElse(List.of());
    boolean hasDynamicRules = rules.stream().anyMatch(codeSystemVersionResolver::isDynamic);
    if (!hasDynamicRules) {
      return true;
    }
    List<ValueSetSnapshotDependency> dependencies = Optional.ofNullable(snapshot.getDependencies()).orElse(List.of());
    if (dependencies.isEmpty()) {
      return false;
    }
    Map<String, ValueSetSnapshotDependency> dependencyMap = dependencies.stream()
        .filter(ValueSetSnapshotDependency::isDynamic)
        .filter(d -> d.getCodeSystem() != null)
        .collect(Collectors.toMap(ValueSetSnapshotDependency::getCodeSystem, d -> d, (left, right) -> left));

    return rules.stream()
        .filter(codeSystemVersionResolver::isDynamic)
        .allMatch(rule -> {
          ValueSetSnapshotDependency dependency = dependencyMap.get(rule.getCodeSystem());
          if (dependency == null) {
            return false;
          }
          CodeSystemVersionReference currentVersion = codeSystemVersionResolver.copyReference(
              codeSystemVersionResolver.resolve(rule.getCodeSystem(), null));
          return Objects.equals(currentVersion == null ? null : currentVersion.getId(),
              dependency.getCodeSystemVersion() == null ? null : dependency.getCodeSystemVersion().getId());
        });
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }
}
