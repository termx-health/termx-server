package org.termx.terminology.terminology.codesystem.concept;

import com.kodality.commons.model.QueryResult;
import org.termx.terminology.terminology.codesystem.CodeSystemRepository;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.ts.PublicationStatus;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemContent;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.CodeSystemVersionQueryParams;
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import org.termx.ts.codesystem.CodeSystemQueryParams;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.Designation;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Singleton
@RequiredArgsConstructor
public class ConceptSupplementService {
  private final ConceptRepository conceptRepository;
  private final CodeSystemRepository codeSystemRepository;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  public List<Concept> mergeRuntimeSupplements(List<Concept> concepts, ConceptQueryParams params) {
    if (CollectionUtils.isEmpty(concepts) || !shouldLoadSupplements(params)) {
      return concepts;
    }

    Map<String, List<ResolvedSupplement>> supplementsByBaseCodeSystem = resolveSupplements(concepts, params);
    if (supplementsByBaseCodeSystem.isEmpty()) {
      return concepts;
    }

    concepts.stream()
        .collect(Collectors.groupingBy(Concept::getCodeSystem))
        .forEach((baseCodeSystem, codeSystemConcepts) -> applySupplements(baseCodeSystem, codeSystemConcepts, supplementsByBaseCodeSystem.get(baseCodeSystem), params));
    return concepts;
  }

  public Concept mergeRuntimeSupplements(Concept concept, ConceptQueryParams params) {
    if (concept == null) {
      return null;
    }
    mergeRuntimeSupplements(List.of(concept), params);
    return concept;
  }

  private void applySupplements(String baseCodeSystem, List<Concept> concepts, List<ResolvedSupplement> supplements, ConceptQueryParams params) {
    if (CollectionUtils.isEmpty(concepts) || CollectionUtils.isEmpty(supplements)) {
      return;
    }

    List<String> codes = concepts.stream().map(Concept::getCode).filter(StringUtils::isNotBlank).distinct().toList();
    if (CollectionUtils.isEmpty(codes)) {
      return;
    }

    Map<String, List<Designation>> supplementDesignations = new LinkedHashMap<>();
    supplements.forEach(supplement -> loadSupplementConcepts(supplement.id(), codes, supplement.version(), params).forEach(concept -> {
      List<Designation> designations = concept.getVersions() == null ? List.of() : concept.getVersions().stream()
          .flatMap(v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream())
          .filter(d -> languageMatches(d.getLanguage(), params.getDisplayLanguage()))
          .map(d -> d.setSupplement(true))
          .toList();
      if (CollectionUtils.isNotEmpty(designations)) {
        supplementDesignations.computeIfAbsent(concept.getCode(), key -> new ArrayList<>()).addAll(designations);
      }
    }));

    concepts.forEach(concept -> mergeConceptDesignations(concept, supplementDesignations.getOrDefault(concept.getCode(), List.of())));
  }

  private List<Concept> loadSupplementConcepts(String supplementCodeSystem, List<String> codes, String version, ConceptQueryParams params) {
    ConceptQueryParams supplementParams = new ConceptQueryParams()
        .setCodeSystem(supplementCodeSystem)
        .setCodes(codes)
        .setCodeSystemVersion(version)
        .setPermittedCodeSystems(params.getPermittedCodeSystems())
        .limit(codes.size());

    QueryResult<Concept> result = conceptRepository.query(supplementParams);
    result.setData(decorate(result.getData(), supplementParams));
    return result.getData();
  }

  private List<Concept> decorate(List<Concept> concepts, ConceptQueryParams params) {
    if (CollectionUtils.isEmpty(concepts)) {
      return concepts;
    }
    CodeSystemEntityVersionQueryParams versionParams = new CodeSystemEntityVersionQueryParams();
    versionParams.setCodeSystem(params.getCodeSystem());
    versionParams.setCodeSystemVersion(params.getCodeSystemVersion());
    versionParams.setCodeSystemVersionId(params.getCodeSystemVersionId());
    versionParams.setCodeSystemVersions(params.getCodeSystemVersions());
    versionParams.all();

    List<String> entityIds = concepts.stream().map(Concept::getId).filter(Objects::nonNull).map(String::valueOf).toList();
    versionParams.setCodeSystemEntityIds(String.join(",", entityIds));
    Map<String, List<CodeSystemEntityVersion>> versions = codeSystemEntityVersionService.query(versionParams).getData().stream()
        .collect(Collectors.groupingBy(CodeSystemEntityVersion::getCode));
    concepts.forEach(concept -> concept.setVersions(versions.getOrDefault(concept.getCode(), concept.getVersions())));
    return concepts;
  }

  private Map<String, List<ResolvedSupplement>> resolveSupplements(List<Concept> concepts, ConceptQueryParams params) {
    Set<String> baseCodeSystems = concepts.stream().map(Concept::getCodeSystem).filter(StringUtils::isNotBlank).collect(Collectors.toCollection(LinkedHashSet::new));
    if (CollectionUtils.isEmpty(baseCodeSystems)) {
      return Map.of();
    }

    Map<String, List<ResolvedSupplement>> result = new LinkedHashMap<>();
    parseRequestedSupplements(params.getUseSupplement()).forEach(requested -> {
      CodeSystem supplement = codeSystemRepository.query(new CodeSystemQueryParams().setUri(requested.uri()).limit(1)).findFirst().orElse(null);
      if (supplement == null || StringUtils.isBlank(supplement.getBaseCodeSystem()) || !baseCodeSystems.contains(supplement.getBaseCodeSystem())) {
        return;
      }
      resolveSupplementVersion(supplement.getId(), requested.version())
          .ifPresent(version -> result.computeIfAbsent(supplement.getBaseCodeSystem(), key -> new ArrayList<>()).add(new ResolvedSupplement(supplement.getId(), version)));
    });

    if (Boolean.TRUE.equals(params.getIncludeSupplement()) && StringUtils.isNotBlank(params.getDisplayLanguage())) {
      baseCodeSystems.forEach(baseCodeSystem -> codeSystemRepository.query(new CodeSystemQueryParams()
              .setBaseCodeSystem(baseCodeSystem)
              .setContent(CodeSystemContent.supplement)
              .all())
          .getData().stream()
          .map(supplement -> resolveSupplementVersion(supplement.getId(), null)
              .map(version -> new ResolvedSupplement(supplement.getId(), version))
              .orElse(null))
          .filter(Objects::nonNull)
          .forEach(supplement -> result.computeIfAbsent(baseCodeSystem, key -> new ArrayList<>()).add(supplement)));
    }

    result.replaceAll((key, value) -> value.stream().distinct().toList());
    return result;
  }

  private Optional<String> resolveSupplementVersion(String supplementCodeSystem, String explicitVersion) {
    if (StringUtils.isNotBlank(explicitVersion)) {
      return Optional.of(explicitVersion);
    }
    return codeSystemVersionService.query(new CodeSystemVersionQueryParams()
            .setCodeSystem(supplementCodeSystem)
            .setStatus(PublicationStatus.active)
            .all())
        .getData().stream()
        .max(Comparator
            .comparing(CodeSystemVersion::getReleaseDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CodeSystemVersion::getCreated, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CodeSystemVersion::getVersion, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(CodeSystemVersion::getVersion);
  }

  private static boolean shouldLoadSupplements(ConceptQueryParams params) {
    return params != null && (Boolean.TRUE.equals(params.getIncludeSupplement()) || StringUtils.isNotBlank(params.getUseSupplement()));
  }

  private static List<RequestedSupplement> parseRequestedSupplements(String useSupplement) {
    if (StringUtils.isBlank(useSupplement)) {
      return List.of();
    }
    return java.util.Arrays.stream(StringUtils.split(useSupplement, ","))
        .filter(StringUtils::isNotBlank)
        .map(String::trim)
        .map(ref -> {
          String[] parts = ref.split("\\|", 2);
          return new RequestedSupplement(parts[0], parts.length > 1 ? parts[1] : null);
        })
        .toList();
  }

  private static void mergeConceptDesignations(Concept concept, List<Designation> supplementDesignations) {
    if (concept == null || CollectionUtils.isEmpty(concept.getVersions()) || CollectionUtils.isEmpty(supplementDesignations)) {
      return;
    }
    concept.getVersions().forEach(version -> {
      List<Designation> designations = new ArrayList<>(Optional.ofNullable(version.getDesignations()).orElse(List.of()));
      designations.addAll(supplementDesignations);
      version.setDesignations(designations.stream()
          .collect(Collectors.collectingAndThen(
              Collectors.toMap(
                  d -> String.join("|", StringUtils.defaultString(d.getDesignationType()), StringUtils.defaultString(d.getLanguage()), StringUtils.defaultString(d.getName())),
                  d -> d,
                  (left, right) -> left,
                  LinkedHashMap::new),
              map -> new ArrayList<>(map.values()))));
    });
  }

  private static boolean languageMatches(String language, String displayLanguage) {
    return StringUtils.isBlank(displayLanguage) || language != null &&
        (language.equals(displayLanguage) || language.startsWith(displayLanguage + "-"));
  }

  private record RequestedSupplement(String uri, String version) {
  }

  private record ResolvedSupplement(String id, String version) {
  }
}
