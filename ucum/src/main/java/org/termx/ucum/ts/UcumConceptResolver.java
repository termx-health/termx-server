package org.termx.ucum.ts;

import com.kodality.commons.model.QueryResult;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.valueset.ValueSetVersionConcept;
import io.micronaut.context.annotation.Value;
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
import org.termx.ts.codesystem.Designation;
import org.termx.ucum.dto.BaseUnitDto;
import org.termx.ucum.dto.DefinedUnitDto;
import org.termx.ucum.service.UcumService;

@Singleton
@RequiredArgsConstructor
public class UcumConceptResolver {
  private static final String UCUM = "ucum";
  private static final String UCUM_URI = "http://unitsofmeasure.org";
  private final UcumMapper mapper;
  private final UcumService ucumService;
  private final UcumSupplementDesignationService ucumSupplementDesignationService;
  @Value("${ucum.ts.cache-ttl-ms:300000}")
  private long cacheTtlMillis = 300000;

  private volatile List<UcumUnitDefinition> unitsCache;
  private volatile Map<String, UcumUnitDefinition> unitByCodeCache;
  private volatile Map<String, String> aliasIndexCache;
  private volatile long cacheExpiresAtMillis;

  public QueryResult<Concept> search(ConceptQueryParams params) {
    String codeEq = StringUtils.isNotEmpty(params.getCodeEq()) ? params.getCodeEq() : params.getCode();
    if (StringUtils.isNotEmpty(codeEq)) {
      Concept concept = findByCode(codeEq).orElse(null);
      return new QueryResult<>(concept == null ? List.of() : List.of(concept));
    }

    List<UcumUnitDefinition> units = getUnits().stream()
        .filter(u -> matchesCodes(u, params.getCodes()))
        .filter(u -> matchesCodeContains(u, params.getCodeContains()))
        .sorted(Comparator.comparing(UcumUnitDefinition::getCode))
        .toList();
    units = mergeUnits(units, ucumSupplementDesignationService.loadSupplementUnitDefinitions(params));
    Map<String, List<Designation>> supplementDesignations = ucumSupplementDesignationService.loadDesignations(
        units.stream().map(UcumUnitDefinition::getCode).toList(), params);
    units = units.stream()
        .filter(u -> matchesDesignationCiEq(u, supplementDesignations.get(u.getCode()), params.getDesignationCiEq()))
        .filter(u -> matchesTextContains(u, supplementDesignations.get(u.getCode()), params.getTextContains()))
        .toList();

    int total = units.size();
    int offset = Optional.ofNullable(params.getOffset()).orElse(0);
    int limit = Optional.ofNullable(params.getLimit()).orElse(total);
    List<Concept> concepts = (offset >= total || limit <= 0)
        ? List.of()
        : units.subList(offset, Math.min(total, offset + limit)).stream().map(mapper::toConcept).toList();
    // meta.total must reflect the full filtered result, not just the returned page, so the UI paging
    // ("load more" when data.length < meta.total) works against virtual UCUM concepts.
    QueryResult<Concept> result = new QueryResult<>(concepts);
    result.getMeta().setTotal(total);
    return result;
  }

  public int count() {
    return getUnits().size();
  }

  public Optional<Concept> findByCode(String code) {
    if (StringUtils.isEmpty(code)) {
      return Optional.empty();
    }
    if (!isValidCode(code)) {
      // The code is not valid UCUM grammar. It may still be a known secondary-code alias (e.g. the ELHR
      // form "mmHg" for "mm[Hg]"); resolve it to its canonical code so $validate-code / $lookup succeed and
      // echo the main code. Guard against a self-referential or non-resolving alias.
      String canonical = getAliasIndex().get(code);
      if (StringUtils.isNotEmpty(canonical) && !canonical.equals(code) && isValidCode(canonical)) {
        return findByCode(canonical);
      }
      return Optional.empty();
    }
    UcumUnitDefinition knownUnit = getUnitByCode().get(code);
    if (knownUnit != null) {
      return Optional.of(mapper.toConcept(knownUnit));
    }
    return Optional.of(mapper.toExpressionConcept(code));
  }

  public Optional<UcumUnitDefinition> findUnitDefinition(String code) {
    if (StringUtils.isEmpty(code)) {
      return Optional.empty();
    }
    return Optional.ofNullable(getUnitByCode().get(code));
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
    // Stamp the UCUM system identity on every concept this provider emits — not only essence atoms matched by
    // findByCode, but also grammar-derived composites (e.g. mmol/L) that findByCode never resolves. Without the
    // uri, $expand emits a `contains` entry with no `system` (invalid FHIR) and system-qualified $validate-code
    // can't match the code against the value set.
    c.getConcept().setCodeSystem(UCUM);
    c.getConcept().setCodeSystemUri(UCUM_URI);
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

  public synchronized void invalidateCache() {
    unitsCache = null;
    unitByCodeCache = null;
    aliasIndexCache = null;
    cacheExpiresAtMillis = 0L;
  }

  private Map<String, String> getAliasIndex() {
    if (aliasIndexCache == null || isCacheExpired()) {
      getUnits();
    }
    Map<String, String> local = aliasIndexCache;
    return local != null ? local : Map.of();
  }

  private List<UcumUnitDefinition> getUnits() {
    List<UcumUnitDefinition> local = unitsCache;
    if (local != null && !isCacheExpired()) {
      return local;
    }
    synchronized (this) {
      if (unitsCache == null || isCacheExpired()) {
        unitsCache = loadUnits();
        unitByCodeCache = unitsCache.stream()
            .collect(Collectors.toMap(UcumUnitDefinition::getCode, u -> u, (a, b) -> a, LinkedHashMap::new));
        aliasIndexCache = ucumSupplementDesignationService.loadAliasIndex();
        cacheExpiresAtMillis = System.currentTimeMillis() + Math.max(cacheTtlMillis, 0L);
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
    ucumSupplementDesignationService.loadSupplementUnitDefinitions().stream()
        .filter(Objects::nonNull)
        .filter(unit -> isValidCode(unit.getCode()))
        .forEach(all::add);

    return all.stream()
        .filter(u -> StringUtils.isNotEmpty(u.getCode()))
        .collect(Collectors.collectingAndThen(
            Collectors.toMap(UcumUnitDefinition::getCode, u -> u, UcumConceptResolver::mergeDefinitions, LinkedHashMap::new),
            m -> new ArrayList<>(m.values())));
  }

  /** Merge two definitions for the same code: keep the first's English names, union the supplement designations. */
  private static UcumUnitDefinition mergeDefinitions(UcumUnitDefinition left, UcumUnitDefinition right) {
    List<Designation> designations = new ArrayList<>(Optional.ofNullable(left.getSupplementDesignations()).orElse(List.of()));
    designations.addAll(Optional.ofNullable(right.getSupplementDesignations()).orElse(List.of()));
    left.setSupplementDesignations(designations);
    if (left.getNames() == null || left.getNames().isEmpty()) {
      left.setNames(right.getNames());
    }
    if (left.getKind() == null) {
      left.setKind(right.getKind());
    }
    if (left.getProperty() == null) {
      left.setProperty(right.getProperty());
    }
    return left;
  }

  private boolean isCacheExpired() {
    return cacheExpiresAtMillis > 0 && System.currentTimeMillis() >= cacheExpiresAtMillis;
  }

  private List<UcumUnitDefinition> mergeUnits(List<UcumUnitDefinition> baseUnits, List<UcumUnitDefinition> additionalUnits) {
    if (additionalUnits == null || additionalUnits.isEmpty()) {
      return baseUnits;
    }
    LinkedHashMap<String, UcumUnitDefinition> merged = new LinkedHashMap<>();
    Optional.ofNullable(baseUnits).orElse(List.of()).forEach(unit -> merged.put(unit.getCode(), unit));
    additionalUnits.stream()
        .filter(Objects::nonNull)
        .filter(unit -> StringUtils.isNotEmpty(unit.getCode()))
        .forEach(unit -> merged.merge(unit.getCode(), unit, (left, right) -> {
          List<String> names = new ArrayList<>(Optional.ofNullable(left.getNames()).orElse(List.of()));
          names.addAll(Optional.ofNullable(right.getNames()).orElse(List.of()));
          left.setNames(names.stream().filter(StringUtils::isNotEmpty).distinct().toList());
          return mergeDefinitions(left, right);
        }));
    return new ArrayList<>(merged.values());
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

  private static boolean matchesDesignationCiEq(UcumUnitDefinition unit, List<Designation> supplementDesignations, String designationCiEq) {
    if (StringUtils.isEmpty(designationCiEq)) {
      return true;
    }
    return unit.getCode().equalsIgnoreCase(designationCiEq)
        || Optional.ofNullable(unit.getNames()).orElse(List.of()).stream().anyMatch(name -> name.equalsIgnoreCase(designationCiEq))
        || designationNames(unit, supplementDesignations).anyMatch(name -> name.equalsIgnoreCase(designationCiEq));
  }

  /** All supplement designation names for a unit: its own cached ones plus any loaded for this search. */
  private static java.util.stream.Stream<String> designationNames(UcumUnitDefinition unit, List<Designation> supplementDesignations) {
    return java.util.stream.Stream.concat(
            Optional.ofNullable(unit.getSupplementDesignations()).orElse(List.of()).stream(),
            Optional.ofNullable(supplementDesignations).orElse(List.of()).stream())
        .map(Designation::getName).filter(Objects::nonNull);
  }

  private static boolean matchesTextContains(UcumUnitDefinition unit, List<Designation> supplementDesignations, String textContains) {
    if (StringUtils.isEmpty(textContains)) {
      return true;
    }
    return containsIgnoreCase(unit.getCode(), textContains)
        || containsIgnoreCase(unit.getKind(), textContains)
        || containsIgnoreCase(unit.getProperty(), textContains)
        || Optional.ofNullable(unit.getNames()).orElse(List.of()).stream().anyMatch(n -> containsIgnoreCase(n, textContains))
        || designationNames(unit, supplementDesignations).anyMatch(n -> containsIgnoreCase(n, textContains));
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
