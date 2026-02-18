package com.kodality.termx.terminology.terminology.valueset.expansion;

import com.kodality.commons.util.DateUtil;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptUtil;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termx.terminology.terminology.valueset.version.ValueSetVersionRepository;
import com.kodality.termx.terminology.terminology.valueset.snapshot.ValueSetSnapshotService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.core.ts.ValueSetExternalExpandProvider;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyQueryParams;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.termx.ts.property.PropertyReference;
import com.kodality.termx.ts.valueset.ValueSetSnapshot;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
  public ValueSetSnapshot expand(String vs, String vsVersion, String preferredLanguage) {
    ValueSetVersion version = getVersion(vs, vsVersion);
    if (version == null) {
      return null;
    }
    ValueSetSnapshot snapshot = version.getSnapshot();
    if (PublicationStatus.active.equals(version.getStatus()) && snapshot != null && snapshot.getExpansion() != null) {
      return snapshot;
    }
    
    List<ValueSetVersionConcept> expansion = expand(version, preferredLanguage);
    snapshot = valueSetSnapshotService.createSnapshot(vs, version.getId(), expansion);

    return snapshot;
  }

  public List<ValueSetVersionConcept> expand(ValueSetVersion version, String preferredLanguage) {
    if (version == null || version.getId() == null) {
      return new ArrayList<>();
    }

    if (PublicationStatus.active.equals(version.getStatus()) && version.getSnapshot() != null && version.getSnapshot().getExpansion() != null) {
      return version.getSnapshot().getExpansion();
    }

    List<ValueSetVersionConcept> expansion = internalExpand(version, preferredLanguage).stream()
        .filter(e -> e.isEnumerated() || e.getConcept().getConceptVersionId() != null)
        .collect(Collectors.toList());
    ValueSetVersionRuleSet ruleSet = version.getRuleSet();
    for (ValueSetExternalExpandProvider provider : externalExpandProviders) {
      expansion.addAll(provider.expand(ruleSet, version, preferredLanguage));
    }
    if (!ruleSet.isInactive()) {
      return expansion.stream().filter(ValueSetVersionConcept::isActive).collect(Collectors.toList());
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
                .filter(d -> c.getDisplay() == null || d != c.getDisplay()).toList());
          }
          c.setActive(calculatedActive(versions));
          c.setStatus(versions.stream().findFirst().map(CodeSystemEntityVersion::getStatus).orElse(PublicationStatus.active));
          c.setAssociations(versions.stream().filter(v -> CollectionUtils.isNotEmpty(v.getAssociations()))
              .flatMap(v -> v.getAssociations().stream()).collect(Collectors.toList()));
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
                    .collect(Collectors.toList()));
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
        }).collect(Collectors.toList());
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

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }
}
