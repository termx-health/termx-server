package org.termx.terminology.terminology.codesystem.concept;

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
import org.termx.ts.valueset.ValueSetVersionConcept;
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

  /**
   * Layers supplement designations onto an already-expanded ValueSet member list (display + additional
   * designations), by base code system and code — the expansion counterpart of {@link #mergeRuntimeSupplements}.
   * The $lookup/$validate-code path merges supplements inside {@link ConceptService#query}; $expand builds its
   * members through other routes (the SQL expand and the external SNOMED expand provider) that never touch that
   * merge, so a supplement on e.g. SNOMED never surfaced in an expansion. This closes that gap for both the
   * stored and inline expand paths. Triggered by {@code useSupplement} or {@code includeSupplement}+displayLanguage.
   */
  public List<UsedSupplement> mergeSupplementsIntoExpansion(List<ValueSetVersionConcept> members, ConceptQueryParams params) {
    if (CollectionUtils.isEmpty(members) || !shouldLoadSupplements(params)) {
      return List.of();
    }
    List<Concept> conceptView = members.stream()
        .map(ValueSetVersionConcept::getConcept).filter(Objects::nonNull)
        .filter(c -> StringUtils.isNotBlank(c.getCode()) && StringUtils.isNotBlank(c.getCodeSystem()))
        .map(c -> {
          Concept view = new Concept();
          view.setCode(c.getCode());
          view.setCodeSystem(c.getCodeSystem());
          return view;
        })
        .toList();
    Map<String, List<ResolvedSupplement>> supplementsByBaseCodeSystem = resolveSupplements(conceptView, params);
    if (supplementsByBaseCodeSystem.isEmpty()) {
      return List.of();
    }

    members.stream()
        .filter(m -> m.getConcept() != null && StringUtils.isNotBlank(m.getConcept().getCodeSystem()))
        .collect(Collectors.groupingBy(m -> m.getConcept().getCodeSystem()))
        .forEach((baseCodeSystem, csMembers) -> {
          List<ResolvedSupplement> supplements = supplementsByBaseCodeSystem.get(baseCodeSystem);
          if (CollectionUtils.isEmpty(supplements)) {
            return;
          }
          List<String> codes = csMembers.stream().map(m -> m.getConcept().getCode()).filter(StringUtils::isNotBlank).distinct().toList();
          Map<String, List<Designation>> byCode = new LinkedHashMap<>();
          supplements.forEach(supplement -> loadSupplementConcepts(supplement.id(), baseCodeSystem, codes, supplement.version(), params).forEach(concept -> {
            // Include ALL of the supplement's designations, not just the displayLanguage's: per FHIR,
            // includeDesignations returns every designation, and displayLanguage only selects which one
            // becomes the display (done in applySupplementDesignations below). Filtering here dropped a
            // useSupplement-named supplement's designations whenever displayLanguage differed (e.g. an
            // explicit Russian supplement requested with displayLanguage=en surfaced nothing).
            List<Designation> designations = concept.getVersions() == null ? List.of() : concept.getVersions().stream()
                .flatMap(v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream())
                .map(d -> d.setSupplement(true))
                .toList();
            if (CollectionUtils.isNotEmpty(designations)) {
              byCode.computeIfAbsent(concept.getCode(), key -> new ArrayList<>()).addAll(designations);
            }
          }));
          csMembers.forEach(member -> applySupplementDesignations(member, byCode.getOrDefault(member.getConcept().getCode(), List.of()), params.getDisplayLanguage()));
        });

    // The supplements actually applied — reported back as `used-supplement` expansion parameters
    // (canonical url + the resolved version), distinct and order-preserving.
    return supplementsByBaseCodeSystem.values().stream()
        .flatMap(List::stream)
        .map(s -> new UsedSupplement(s.url(), s.version()))
        .filter(s -> StringUtils.isNotBlank(s.url()))
        .distinct()
        .toList();
  }

  /** Appends supplement designations to a member (deduped) and, when a displayLanguage is requested, surfaces a matching supplement designation as the member's display. */
  private static void applySupplementDesignations(ValueSetVersionConcept member, List<Designation> supplementDesignations, String displayLanguage) {
    if (CollectionUtils.isEmpty(supplementDesignations)) {
      return;
    }
    List<Designation> additional = new ArrayList<>(Optional.ofNullable(member.getAdditionalDesignations()).orElse(List.of()));
    supplementDesignations.forEach(d -> {
      if (additional.stream().noneMatch(a -> designationKey(a).equals(designationKey(d)))) {
        additional.add(d);
      }
    });
    member.setAdditionalDesignations(additional);
    if (StringUtils.isNotBlank(displayLanguage)) {
      supplementDesignations.stream()
          .filter(d -> languageMatches(d.getLanguage(), displayLanguage) && d.getName() != null)
          .findFirst()
          .ifPresent(member::setDisplay);
    }
  }

  private static String designationKey(Designation d) {
    return d.getLanguage() + "|" + d.getDesignationType() + "|" + d.getName();
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
    supplements.forEach(supplement -> loadSupplementConcepts(supplement.id(), baseCodeSystem, codes, supplement.version(), params).forEach(concept -> {
      // Include ALL supplement designations regardless of displayLanguage (it only selects the display) —
      // see mergeSupplementsIntoExpansion.
      String source = new UsedSupplement(supplement.url(), supplement.version()).asCanonical();
      List<Designation> designations = concept.getVersions() == null ? List.of() : concept.getVersions().stream()
          .flatMap(v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream())
          .map(d -> d.setSupplement(true).setSupplementSource(source))
          .toList();
      if (CollectionUtils.isNotEmpty(designations)) {
        supplementDesignations.computeIfAbsent(concept.getCode(), key -> new ArrayList<>()).addAll(designations);
      }
    }));

    concepts.forEach(concept -> mergeConceptDesignations(concept, supplementDesignations.getOrDefault(concept.getCode(), List.of())));
  }

  /**
   * Loads a supplement's localized designations for the given base-system codes. A {@code content=supplement}
   * code system does NOT create its own concept rows: its designations live on code system entity versions
   * whose ENTITY belongs to the base code system but which are MEMBERS of the supplement's code system
   * version (linked via {@code base_entity_version_id}). So we cannot find them through {@code ConceptRepository}
   * by entity {@code code_system} (that yielded nothing) — we load the entity versions by code-system-version
   * membership ({@code <supplement>|<version>}) and read their designations.
   */
  private List<Concept> loadSupplementConcepts(String supplementCodeSystem, String baseCodeSystem, List<String> codes, String version, ConceptQueryParams params) {
    CodeSystemEntityVersionQueryParams versionParams = new CodeSystemEntityVersionQueryParams();
    versionParams.setCodeSystemVersions(supplementCodeSystem + "|" + version);
    versionParams.setCode(String.join(",", codes));
    // The membership query filters permitted code systems on BOTH the entity's code system (the base) and the
    // version's code system (the supplement); a null (unrestricted) list matches NOTHING, so pass both
    // explicitly when unrestricted. A restricted caller's grants are honored as-is.
    versionParams.setPermittedCodeSystems(params.getPermittedCodeSystems() != null
        ? params.getPermittedCodeSystems()
        : List.of(baseCodeSystem, supplementCodeSystem));
    versionParams.all();

    return codeSystemEntityVersionService.query(versionParams).getData().stream()
        .collect(Collectors.groupingBy(CodeSystemEntityVersion::getCode, LinkedHashMap::new, Collectors.toList()))
        .entrySet().stream()
        .map(entry -> {
          Concept concept = new Concept();
          concept.setCode(entry.getKey());
          concept.setCodeSystem(supplementCodeSystem);
          concept.setVersions(entry.getValue());
          return concept;
        })
        .toList();
  }

  private Map<String, List<ResolvedSupplement>> resolveSupplements(List<Concept> concepts, ConceptQueryParams params) {
    Set<String> baseCodeSystems = concepts.stream().map(Concept::getCodeSystem).filter(StringUtils::isNotBlank).collect(Collectors.toCollection(LinkedHashSet::new));
    if (CollectionUtils.isEmpty(baseCodeSystems)) {
      return Map.of();
    }

    Map<String, List<ResolvedSupplement>> result = new LinkedHashMap<>();
    parseRequestedSupplements(params.getUseSupplement()).forEach(requested -> {
      CodeSystem supplement = codeSystemRepository.query(new CodeSystemQueryParams().setUri(requested.uri()).limit(1)).findFirst().orElse(null);
      // A `useSupplement` that names a supplement the server does not host is a hard error (tx-ecosystem
      // VALUESET_SUPPLEMENT_MISSING) — a not-found OperationOutcome, NOT a silent no-op. A supplement that
      // exists but targets a different base code system than any being expanded is simply not applicable
      // here, so it is skipped (not every requested supplement applies to every system in the request).
      if (supplement == null) {
        throw org.termx.terminology.fhir.TxIssues.notFoundException(404, "Required supplement not found: " + requested.uri());
      }
      if (StringUtils.isBlank(supplement.getBaseCodeSystem()) || !baseCodeSystems.contains(supplement.getBaseCodeSystem())) {
        return;
      }
      resolveSupplementVersion(supplement.getId(), requested.version())
          .ifPresent(version -> result.computeIfAbsent(supplement.getBaseCodeSystem(), key -> new ArrayList<>())
              .add(new ResolvedSupplement(supplement.getId(), version, supplement.getUri())));
    });

    if (Boolean.TRUE.equals(params.getIncludeSupplement()) && StringUtils.isNotBlank(params.getDisplayLanguage())) {
      baseCodeSystems.forEach(baseCodeSystem -> codeSystemRepository.query(new CodeSystemQueryParams()
              .setBaseCodeSystem(baseCodeSystem)
              .setContent(CodeSystemContent.supplement)
              .all())
          .getData().stream()
          .map(supplement -> resolveSupplementVersion(supplement.getId(), null)
              .map(version -> new ResolvedSupplement(supplement.getId(), version, supplement.getUri()))
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

  private record ResolvedSupplement(String id, String version, String url) {
  }

  /** A supplement that was applied to an expansion/lookup — its canonical url and the resolved version,
   *  reported back as a {@code used-supplement} expansion parameter ({@code url|version}). */
  public record UsedSupplement(String url, String version) {
    public String asCanonical() {
      return StringUtils.isBlank(version) ? url : url + "|" + version;
    }
  }
}
