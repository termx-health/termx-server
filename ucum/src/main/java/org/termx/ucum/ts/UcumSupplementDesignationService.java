package org.termx.ucum.ts;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.termx.terminology.terminology.codesystem.CodeSystemRepository;
import org.termx.terminology.terminology.codesystem.concept.ConceptRepository;
import org.termx.terminology.terminology.codesystem.designation.DesignationService;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionRepository;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionRepository;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemContent;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import org.termx.ts.codesystem.CodeSystemQueryParams;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.CodeSystemVersionQueryParams;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.DesignationQueryParams;
import org.termx.ts.valueset.ValueSetVersionConcept;

@Singleton
@RequiredArgsConstructor
public class UcumSupplementDesignationService {
  private static final String UCUM = "ucum";
  private static final String UCUM_URI = "http://unitsofmeasure.org";

  private final CodeSystemRepository codeSystemRepository;
  private final CodeSystemVersionRepository codeSystemVersionRepository;
  private final ConceptRepository conceptRepository;
  private final CodeSystemEntityVersionRepository codeSystemEntityVersionRepository;
  private final DesignationService designationService;

  public void enrich(List<ValueSetVersionConcept> concepts, String preferredLanguage) {
    if (CollectionUtils.isEmpty(concepts)) {
      return;
    }
    List<ValueSetVersionConcept> ucumConcepts = concepts.stream()
        .filter(c -> c != null && c.getConcept() != null && StringUtils.isNotEmpty(c.getConcept().getCode()))
        .filter(c -> UCUM.equals(c.getConcept().getCodeSystem()) || UCUM_URI.equals(c.getConcept().getCodeSystemUri()))
        .toList();
    if (ucumConcepts.isEmpty()) {
      return;
    }

    Map<String, List<Designation>> supplementDesignations = loadDesignations(
        ucumConcepts.stream().map(c -> c.getConcept().getCode()).distinct().toList(),
        preferredLanguage, null, true);

    ucumConcepts.forEach(c -> {
      List<Designation> merged = new ArrayList<>(Optional.ofNullable(c.getAdditionalDesignations()).orElse(List.of()));
      merged.addAll(supplementDesignations.getOrDefault(c.getConcept().getCode(), List.of()));
      c.setAdditionalDesignations(distinctDesignations(merged));
    });
  }

  public Map<String, List<Designation>> loadDesignations(List<String> codes, ConceptQueryParams params) {
    if (CollectionUtils.isEmpty(codes) || params == null) {
      return Map.of();
    }
    return loadDesignations(codes, params.getDisplayLanguage(), params.getUseSupplement(), params.getIncludeSupplement());
  }

  public List<UcumUnitDefinition> loadSupplementUnitDefinitions() {
    return loadSupplementUnitDefinitions(loadUcumSupplements().stream()
        .map(codeSystem -> resolveSupplementVersion(codeSystem.getId(), null)
            .map(version -> new ResolvedSupplement(codeSystem.getId(), version))
            .orElse(null))
        .filter(Objects::nonNull)
        .toList());
  }

  public List<UcumUnitDefinition> loadSupplementUnitDefinitions(ConceptQueryParams params) {
    if (params == null || StringUtils.isEmpty(params.getUseSupplement())) {
      return List.of();
    }
    return loadSupplementUnitDefinitions(resolveSupplements(params.getDisplayLanguage(), params.getUseSupplement(), params.getIncludeSupplement()));
  }

  private List<UcumUnitDefinition> loadSupplementUnitDefinitions(List<ResolvedSupplement> supplements) {
    List<UcumUnitDefinition> units = new ArrayList<>();
    for (ResolvedSupplement supplement : supplements) {
      List<Concept> concepts = conceptRepository.query(new ConceptQueryParams()
          .setCodeSystem(supplement.id())
          .all()).getData();
      if (CollectionUtils.isEmpty(concepts)) {
        continue;
      }

      List<String> conceptIds = concepts.stream().map(Concept::getId).filter(Objects::nonNull).map(String::valueOf).toList();
      if (conceptIds.isEmpty()) {
        continue;
      }

      List<CodeSystemEntityVersion> versions = codeSystemEntityVersionRepository.query(new CodeSystemEntityVersionQueryParams()
          .setCodeSystem(supplement.id())
          .setCodeSystemVersion(supplement.version())
          .setCodeSystemEntityIds(String.join(",", conceptIds))
          .limit(conceptIds.size())).getData();
      if (CollectionUtils.isEmpty(versions)) {
        continue;
      }

      Map<Long, List<Designation>> designationsByVersionId = designationService.query(new DesignationQueryParams()
          .setCodeSystemEntityVersionId(String.join(",", versions.stream().map(CodeSystemEntityVersion::getId).filter(Objects::nonNull).map(String::valueOf).toList()))
          .limit(-1)).getData().stream()
          .collect(Collectors.groupingBy(Designation::getCodeSystemEntityVersionId));

      versions.stream()
          .filter(v -> StringUtils.isNotEmpty(v.getCode()))
          .forEach(version -> units.add(new UcumUnitDefinition()
              .setCode(version.getCode())
              .setNames(distinctDesignations(designationsByVersionId.getOrDefault(version.getId(), List.of())).stream()
                  .map(Designation::getName)
                  .filter(StringUtils::isNotEmpty)
                  .toList())));
    }
    return units.stream()
        .filter(unit -> StringUtils.isNotEmpty(unit.getCode()))
        .collect(Collectors.collectingAndThen(
            Collectors.toMap(UcumUnitDefinition::getCode, unit -> unit, (left, right) -> {
              List<String> names = new ArrayList<>(Optional.ofNullable(left.getNames()).orElse(List.of()));
              names.addAll(Optional.ofNullable(right.getNames()).orElse(List.of()));
              left.setNames(names.stream().filter(StringUtils::isNotEmpty).distinct().toList());
              return left;
            }, LinkedHashMap::new),
            map -> new ArrayList<>(map.values())));
  }

  private Map<String, List<Designation>> loadDesignations(List<String> codes, String preferredLanguage, String useSupplement, Boolean includeSupplement) {
    if (CollectionUtils.isEmpty(codes)) {
      return Map.of();
    }

    List<ResolvedSupplement> supplements = resolveSupplements(preferredLanguage, useSupplement, includeSupplement);
    if (supplements.isEmpty()) {
      return Map.of();
    }

    Map<String, List<Designation>> supplementDesignations = new LinkedHashMap<>();
    for (ResolvedSupplement supplement : supplements) {
      List<Concept> concepts = conceptRepository.query(new ConceptQueryParams()
          .setCodeSystem(supplement.id())
          .setCodes(codes)
          .limit(codes.size())).getData();
      if (CollectionUtils.isEmpty(concepts)) {
        continue;
      }

      List<String> conceptIds = concepts.stream().map(Concept::getId).filter(Objects::nonNull).map(String::valueOf).toList();
      if (conceptIds.isEmpty()) {
        continue;
      }

      List<CodeSystemEntityVersion> versions = codeSystemEntityVersionRepository.query(new CodeSystemEntityVersionQueryParams()
          .setCodeSystem(supplement.id())
          .setCodeSystemVersion(supplement.version())
          .setCodeSystemEntityIds(String.join(",", conceptIds))
          .limit(conceptIds.size())).getData();
      if (CollectionUtils.isEmpty(versions)) {
        continue;
      }

      Map<Long, String> versionIdToCode = versions.stream()
          .filter(v -> v.getId() != null && StringUtils.isNotEmpty(v.getCode()))
          .collect(Collectors.toMap(CodeSystemEntityVersion::getId, CodeSystemEntityVersion::getCode, (left, right) -> left, LinkedHashMap::new));
      if (versionIdToCode.isEmpty()) {
        continue;
      }

      List<Designation> designations = designationService.query(new DesignationQueryParams()
          .setCodeSystemEntityVersionId(String.join(",", versionIdToCode.keySet().stream().map(String::valueOf).toList()))
          .limit(-1)).getData();
      designations.stream()
          .filter(d -> languageMatches(d.getLanguage(), preferredLanguage))
          .forEach(d -> supplementDesignations.computeIfAbsent(versionIdToCode.get(d.getCodeSystemEntityVersionId()), key -> new ArrayList<>())
              .add(copyDesignation(d).setSupplement(true)));
    }

    supplementDesignations.replaceAll((code, designations) -> distinctDesignations(designations));
    return supplementDesignations;
  }

  private List<ResolvedSupplement> resolveSupplements(String preferredLanguage, String useSupplement, Boolean includeSupplement) {
    Map<String, ResolvedSupplement> supplements = new LinkedHashMap<>();

    parseRequestedSupplements(useSupplement).forEach(requested -> resolveSupplement(requested.reference())
        .flatMap(supplement -> resolveSupplementVersion(supplement.getId(), requested.version())
            .map(version -> new ResolvedSupplement(supplement.getId(), version)))
        .ifPresent(supplement -> supplements.putIfAbsent(supplement.id(), supplement)));

    if (Boolean.TRUE.equals(includeSupplement) && StringUtils.isNotEmpty(preferredLanguage)) {
      loadUcumSupplements().forEach(supplement -> resolveSupplementVersion(supplement.getId(), null)
          .map(version -> new ResolvedSupplement(supplement.getId(), version))
          .ifPresent(resolved -> supplements.putIfAbsent(resolved.id(), resolved)));
    }

    return new ArrayList<>(supplements.values());
  }

  private List<CodeSystem> loadUcumSupplements() {
    return codeSystemRepository.query(new CodeSystemQueryParams()
            .setContent(CodeSystemContent.supplement)
            .all())
        .getData().stream()
        .filter(cs -> UCUM.equals(cs.getBaseCodeSystem()) || UCUM_URI.equals(cs.getBaseCodeSystemUri()))
        .toList();
  }

  private Optional<CodeSystem> resolveSupplement(String reference) {
    if (StringUtils.isEmpty(reference)) {
      return Optional.empty();
    }
    List<CodeSystem> byUri = codeSystemRepository.query(new CodeSystemQueryParams().setUri(reference).limit(1)).getData();
    if (CollectionUtils.isNotEmpty(byUri)) {
      return byUri.stream().findFirst();
    }
    CodeSystem supplement = codeSystemRepository.load(reference);
    return Optional.ofNullable(supplement)
        .filter(cs -> UCUM.equals(cs.getBaseCodeSystem()) || UCUM_URI.equals(cs.getBaseCodeSystemUri()));
  }

  private Optional<String> resolveSupplementVersion(String supplementCodeSystem, String explicitVersion) {
    if (StringUtils.isNotEmpty(explicitVersion)) {
      return Optional.ofNullable(codeSystemVersionRepository.load(supplementCodeSystem, explicitVersion))
          .map(CodeSystemVersion::getVersion);
    }
    return codeSystemVersionRepository.query(new CodeSystemVersionQueryParams()
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

  private static List<RequestedSupplement> parseRequestedSupplements(String useSupplement) {
    if (StringUtils.isEmpty(useSupplement)) {
      return List.of();
    }
    return java.util.Arrays.stream(org.apache.commons.lang3.StringUtils.split(useSupplement, ","))
        .filter(StringUtils::isNotEmpty)
        .map(String::trim)
        .map(ref -> {
          String[] parts = ref.split("\\|", 2);
          return new RequestedSupplement(parts[0], parts.length > 1 ? parts[1] : null);
        })
        .toList();
  }

  private static boolean languageMatches(String language, String preferredLanguage) {
    return StringUtils.isEmpty(preferredLanguage) || language != null &&
        (language.equals(preferredLanguage) || language.startsWith(preferredLanguage + "-"));
  }

  private static Designation copyDesignation(Designation designation) {
    return new Designation()
        .setCodeSystemEntityVersionId(designation.getCodeSystemEntityVersionId())
        .setDesignationType(designation.getDesignationType())
        .setLanguage(designation.getLanguage())
        .setName(designation.getName())
        .setPreferred(designation.isPreferred())
        .setStatus(designation.getStatus())
        .setSupplement(designation.isSupplement());
  }

  private static List<Designation> distinctDesignations(List<Designation> designations) {
    return designations.stream().collect(Collectors.collectingAndThen(
        Collectors.toMap(d -> String.join("|",
                org.apache.commons.lang3.StringUtils.defaultString(d.getDesignationType()),
                org.apache.commons.lang3.StringUtils.defaultString(d.getLanguage()),
                org.apache.commons.lang3.StringUtils.defaultString(d.getName())),
            d -> d, (a, b) -> a, LinkedHashMap::new),
        m -> new ArrayList<>(m.values())));
  }

  private record RequestedSupplement(String reference, String version) {
  }

  private record ResolvedSupplement(String id, String version) {
  }
}
