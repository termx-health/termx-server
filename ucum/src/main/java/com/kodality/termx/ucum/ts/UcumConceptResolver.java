package com.kodality.termx.ucum.ts;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.termx.ucum.dto.BaseUnitDto;
import org.termx.ucum.dto.DefinedUnitDto;
import org.termx.ucum.service.UcumService;

@Singleton
@RequiredArgsConstructor
public class UcumConceptResolver {
  private final UcumMapper mapper;
  private final UcumService ucumService;

  private volatile List<UcumUnitDefinition> unitsCache;
  private volatile Map<String, UcumUnitDefinition> unitByCodeCache;

  public QueryResult<Concept> search(ConceptQueryParams params) {
    String codeEq = StringUtils.isNotEmpty(params.getCodeEq()) ? params.getCodeEq() : params.getCode();
    if (StringUtils.isNotEmpty(codeEq)) {
      Concept concept = findByCode(codeEq).orElse(null);
      return new QueryResult<>(concept == null ? List.of() : List.of(concept));
    }

    List<UcumUnitDefinition> units = getUnits().stream()
        .filter(u -> matchesCodes(u, params.getCodes()))
        .filter(u -> matchesCodeContains(u, params.getCodeContains()))
        .filter(u -> matchesDesignationCiEq(u, params.getDesignationCiEq()))
        .filter(u -> matchesTextContains(u, params.getTextContains()))
        .sorted(Comparator.comparing(UcumUnitDefinition::getCode))
        .toList();

    int offset = Optional.ofNullable(params.getOffset()).orElse(0);
    int limit = Optional.ofNullable(params.getLimit()).orElse(units.size());
    if (offset >= units.size() || limit <= 0) {
      return new QueryResult<>(List.of());
    }
    int to = Math.min(units.size(), offset + limit);
    List<Concept> concepts = units.subList(offset, to).stream().map(mapper::toConcept).toList();
    return new QueryResult<>(concepts);
  }

  public Optional<Concept> findByCode(String code) {
    if (StringUtils.isEmpty(code)) {
      return Optional.empty();
    }
    UcumUnitDefinition knownUnit = getUnitByCode().get(code);
    if (knownUnit != null) {
      return Optional.of(mapper.toConcept(knownUnit));
    }
    return isValidCode(code) ? Optional.of(mapper.toExpressionConcept(code)) : Optional.empty();
  }

  public List<ValueSetVersionConcept> expandByKind(Object filterValue) {
    Set<String> requested = asNormalizedValueSet(filterValue);
    return getUnits().stream()
        .filter(u -> requested.isEmpty() || matchesKindOrProperty(u, requested))
        .sorted(Comparator.comparing(UcumUnitDefinition::getCode))
        .map(u -> new ValueSetVersionConcept()
            .setConcept(mapper.toVSConcept(u))
            .setAdditionalDesignations(mapper.toConceptVersion(u).getDesignations())
            .setActive(true))
        .toList();
  }

  public void decorate(ValueSetVersionConcept c) {
    if (c == null || c.getConcept() == null || StringUtils.isEmpty(c.getConcept().getCode())) {
      return;
    }
    findByCode(c.getConcept().getCode()).ifPresent(concept -> {
      if (c.getAdditionalDesignations() == null || c.getAdditionalDesignations().isEmpty()) {
        c.setAdditionalDesignations(concept.getVersions().stream().findFirst().map(v -> v.getDesignations()).orElse(List.of()));
      }
      c.setActive(true);
    });
    if (!isValidCode(c.getConcept().getCode())) {
      c.setActive(false);
    }
  }

  private List<UcumUnitDefinition> getUnits() {
    List<UcumUnitDefinition> local = unitsCache;
    if (local != null) {
      return local;
    }
    synchronized (this) {
      if (unitsCache == null) {
        unitsCache = loadUnits();
        unitByCodeCache = unitsCache.stream()
            .collect(Collectors.toMap(UcumUnitDefinition::getCode, u -> u, (a, b) -> a, LinkedHashMap::new));
      }
      return unitsCache;
    }
  }

  private Map<String, UcumUnitDefinition> getUnitByCode() {
    if (unitByCodeCache == null) {
      getUnits();
    }
    return unitByCodeCache;
  }

  private List<UcumUnitDefinition> loadUnits() {
    List<UcumUnitDefinition> all = new ArrayList<>();
    List<BaseUnitDto> baseUnits = Optional.ofNullable(ucumService.getBaseUnits()).orElse(List.of());
    List<DefinedUnitDto> definedUnits = Optional.ofNullable(ucumService.getDefinedUnits()).orElse(List.of());

    baseUnits.stream().map(this::toUnit).filter(Objects::nonNull).forEach(all::add);
    definedUnits.stream().map(this::toUnit).filter(Objects::nonNull).forEach(all::add);

    return all.stream()
        .filter(u -> StringUtils.isNotEmpty(u.getCode()))
        .collect(Collectors.collectingAndThen(Collectors.toMap(UcumUnitDefinition::getCode, u -> u, (a, b) -> a, LinkedHashMap::new),
            m -> new ArrayList<>(m.values())));
  }

  private UcumUnitDefinition toUnit(BaseUnitDto dto) {
    if (dto == null || StringUtils.isEmpty(dto.getCode())) {
      return null;
    }
    return new UcumUnitDefinition()
        .setCode(dto.getCode())
        .setKind(dto.getKind())
        .setProperty(dto.getProperty())
        .setNames(dto.getNames());
  }

  private UcumUnitDefinition toUnit(DefinedUnitDto dto) {
    if (dto == null || StringUtils.isEmpty(dto.getCode())) {
      return null;
    }
    return new UcumUnitDefinition()
        .setCode(dto.getCode())
        .setKind(dto.getKind())
        .setProperty(dto.getProperty())
        .setNames(dto.getNames());
  }

  private boolean isValidCode(String code) {
    return Optional.ofNullable(ucumService.validate(code)).map(v -> v.isValid()).orElse(false);
  }

  private static boolean matchesCodes(UcumUnitDefinition unit, List<String> codes) {
    return codes == null || codes.isEmpty() || codes.contains(unit.getCode());
  }

  private static boolean matchesCodeContains(UcumUnitDefinition unit, String codeContains) {
    return StringUtils.isEmpty(codeContains) || containsIgnoreCase(unit.getCode(), codeContains);
  }

  private static boolean matchesDesignationCiEq(UcumUnitDefinition unit, String designationCiEq) {
    return StringUtils.isEmpty(designationCiEq) || unit.getCode().equalsIgnoreCase(designationCiEq);
  }

  private static boolean matchesTextContains(UcumUnitDefinition unit, String textContains) {
    if (StringUtils.isEmpty(textContains)) {
      return true;
    }
    return containsIgnoreCase(unit.getCode(), textContains)
        || containsIgnoreCase(unit.getKind(), textContains)
        || containsIgnoreCase(unit.getProperty(), textContains)
        || Optional.ofNullable(unit.getNames()).orElse(List.of()).stream().anyMatch(n -> containsIgnoreCase(n, textContains));
  }

  private static boolean matchesKindOrProperty(UcumUnitDefinition unit, Set<String> requested) {
    String kind = normalize(unit.getKind());
    String property = normalize(unit.getProperty());
    return requested.contains(kind) || requested.contains(property);
  }

  private static Set<String> asNormalizedValueSet(Object value) {
    if (value == null) {
      return Set.of();
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().filter(Objects::nonNull).map(Object::toString).map(UcumConceptResolver::normalize)
          .filter(StringUtils::isNotEmpty).collect(Collectors.toCollection(LinkedHashSet::new));
    }
    String stringValue = value.toString();
    if (StringUtils.isEmpty(stringValue)) {
      return Set.of();
    }
    String trimmed = stringValue.trim();
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }
    return java.util.Arrays.stream(trimmed.split(","))
        .map(s -> s == null ? null : s.trim())
        .filter(StringUtils::isNotEmpty)
        .map(s -> s.startsWith("\"") && s.endsWith("\"") && s.length() > 1 ? s.substring(1, s.length() - 1) : s)
        .map(UcumConceptResolver::normalize)
        .filter(StringUtils::isNotEmpty)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean containsIgnoreCase(String value, String contains) {
    return value != null && contains != null && value.toLowerCase(Locale.ROOT).contains(contains.toLowerCase(Locale.ROOT));
  }
}
