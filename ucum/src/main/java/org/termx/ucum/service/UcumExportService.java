package org.termx.ucum.service;

import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemContent;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import org.termx.ucum.ts.UcumConceptResolver;
import org.termx.ucum.ts.UcumUnitDefinition;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.termx.ucum.dto.UcumExportDesignationDto;
import org.termx.ucum.dto.UcumExportRequestDto;
import org.termx.ucum.dto.UcumExportResponseDto;
import org.termx.ucum.dto.UcumExportUnitDto;

@Singleton
@RequiredArgsConstructor
public class UcumExportService {
  private static final String UCUM = "ucum";
  private static final String UCUM_URI = "http://unitsofmeasure.org";

  private final UcumConceptResolver ucumConceptResolver;
  private final CodeSystemService codeSystemService;
  private final ConceptService conceptService;

  public UcumExportResponseDto export(UcumExportRequestDto request) {
    List<CodeSystem> supplements = resolveSupplements(request == null ? null : request.getSupplements());
    Map<String, List<Designation>> supplementDesignations = loadSupplementDesignations(supplements);

    LinkedHashSet<String> codes = new LinkedHashSet<>();
    codes.addAll(loadBaseCodes());
    codes.addAll(supplementDesignations.keySet());

    List<UcumExportUnitDto> units = codes.stream()
        .map(code -> toExportUnit(code, supplementDesignations.getOrDefault(code, List.of())))
        .sorted(java.util.Comparator.comparing(UcumExportUnitDto::getCode))
        .toList();

    UcumExportResponseDto response = new UcumExportResponseDto();
    response.setSupplements(supplements.stream().map(cs -> StringUtils.defaultIfBlank(cs.getUri(), cs.getId())).toList());
    response.setUnits(units);
    return response;
  }

  private List<String> loadBaseCodes() {
    return ucumConceptResolver.search(new ConceptQueryParams().setCodeSystem(UCUM).all()).getData().stream()
        .map(Concept::getCode)
        .toList();
  }

  private List<CodeSystem> resolveSupplements(List<String> requestedSupplements) {
    List<CodeSystem> allSupplements = codeSystemService.query(new CodeSystemQueryParams()
        .setContent(CodeSystemContent.supplement)
        .setVersionsDecorated(true)
        .all()).getData().stream()
        .filter(cs -> UCUM.equals(cs.getBaseCodeSystem()) || UCUM_URI.equals(cs.getBaseCodeSystemUri()))
        .toList();
    if (CollectionUtils.isEmpty(requestedSupplements)) {
      return allSupplements;
    }
    Set<String> requested = new LinkedHashSet<>(requestedSupplements);
    List<CodeSystem> resolved = allSupplements.stream()
        .filter(cs -> requested.contains(cs.getId()) || requested.contains(cs.getUri()))
        .toList();
    if (resolved.size() != requested.size()) {
      Set<String> found = resolved.stream()
          .flatMap(cs -> java.util.stream.Stream.of(cs.getId(), cs.getUri()))
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());
      List<String> missing = requested.stream().filter(r -> !found.contains(r)).toList();
      throw new IllegalArgumentException("Unknown UCUM supplements: " + String.join(", ", missing));
    }
    return resolved;
  }

  private Map<String, List<Designation>> loadSupplementDesignations(List<CodeSystem> supplements) {
    Map<String, List<Designation>> result = new LinkedHashMap<>();
    for (CodeSystem supplement : supplements) {
      String version = supplement.getLastVersion().map(v -> v.getVersion()).orElse(null);
      List<Concept> concepts = conceptService.query(new ConceptQueryParams()
          .setCodeSystem(supplement.getId())
          .setCodeSystemVersion(version)
          .all()).getData();
      concepts.forEach(concept -> result.computeIfAbsent(concept.getCode(), key -> new ArrayList<>())
          .addAll(Optional.ofNullable(concept.getVersions()).orElse(List.of()).stream()
              .flatMap(v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream())
              .map(d -> copyDesignation(d).setSupplement(true))
              .toList()));
    }
    return result;
  }

  private UcumExportUnitDto toExportUnit(String code, List<Designation> supplementDesignations) {
    List<Designation> designations = new ArrayList<>(ucumConceptResolver.findByCode(code)
        .flatMap(c -> c.getVersions().stream().findFirst())
        .map(v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()))
        .orElse(List.of()));
    designations.addAll(supplementDesignations);
    designations = distinctDesignations(designations);

    UcumUnitDefinition definition = ucumConceptResolver.findUnitDefinition(code).orElse(null);
    UcumExportUnitDto unit = new UcumExportUnitDto();
    unit.setCode(code);
    unit.setKind(definition == null ? null : definition.getKind());
    unit.setProperty(definition == null ? null : definition.getProperty());
    unit.setDesignations(designations.stream().map(this::toDto).toList());
    return unit;
  }

  private UcumExportDesignationDto toDto(Designation designation) {
    UcumExportDesignationDto dto = new UcumExportDesignationDto();
    dto.setType(designation.getDesignationType());
    dto.setLanguage(designation.getLanguage());
    dto.setValue(designation.getName());
    dto.setPreferred(designation.isPreferred());
    dto.setSupplement(designation.isSupplement());
    return dto;
  }

  private static Designation copyDesignation(Designation designation) {
    return new Designation()
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
                StringUtils.defaultString(d.getDesignationType()),
                StringUtils.defaultString(d.getLanguage()),
                StringUtils.defaultString(d.getName())),
            d -> d, (a, b) -> a, LinkedHashMap::new),
        m -> new ArrayList<>(m.values())));
  }
}
