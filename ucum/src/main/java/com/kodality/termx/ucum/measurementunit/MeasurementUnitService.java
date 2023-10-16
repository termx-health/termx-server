package com.kodality.termx.ucum.measurementunit;

import com.kodality.commons.model.LocalizedName;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ucum.measurementunit.mapping.MeasurementUnitMappingRepository;
import com.kodality.termx.ucum.MeasurementUnit;
import com.kodality.termx.ucum.MeasurementUnitQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MeasurementUnitService {
  private final MeasurementUnitRepository measurementUnitRepository;
  private final MeasurementUnitMappingRepository measurementUnitMappingRepository;

  public QueryResult<MeasurementUnit> query(MeasurementUnitQueryParams params) {
    return measurementUnitRepository.query(params);
  }

  public List<String> loadKinds() {
    return measurementUnitRepository.loadKinds();
  }

  public MeasurementUnit load(Long id) {
    return measurementUnitRepository.load(id);
  }

  public MeasurementUnit load(String code) {
    return measurementUnitRepository.load(code);
  }

  @Transactional
  public void save(MeasurementUnit unit) {
    measurementUnitRepository.save(unit);
    measurementUnitMappingRepository.save(unit.getId(), unit.getMappings());
  }

  public Map<String, MeasurementUnit> findMapped(String kind, String system) {
    List<MeasurementUnit> measurementUnits = queryByKind(kind).getData();
    Map<String, MeasurementUnit> result = new HashMap<>();
    measurementUnits.forEach(unit -> {
      if (unit.getMappings() != null) {
        unit.getMappings().forEach(mapping -> {
          if (system.equals(mapping.getSystem())) {
            result.put(mapping.getSystemUnit(), unit);
          }
        });
      }
    });
    return result;
  }

  private QueryResult<MeasurementUnit> queryByKind(String kind) {
    MeasurementUnitQueryParams params = new MeasurementUnitQueryParams();
    params.setKind(kind);
    params.all();
    return query(params);
  }

  public void merge(MeasurementUnit unit) {
    MeasurementUnitQueryParams params = new MeasurementUnitQueryParams();
    params.setCode(unit.getCode());
    params.setDate(LocalDate.now());
    params.setLimit(1);
    Optional<MeasurementUnit> persisted = query(params).findFirst();
    persisted.ifPresent(p -> {
      unit.setId(p.getId());
      unit.setNames(CollectionUtils.isNotEmpty(unit.getNames()) ? mergeLocalizedName(unit.getNames(), p.getNames()) : p.getNames());
      unit.setAlias(CollectionUtils.isNotEmpty(unit.getAlias()) ? mergeLocalizedName(unit.getAlias(), p.getAlias()) : p.getAlias());
      unit.setPeriod(unit.getPeriod() != null && unit.getPeriod().getLower() != null ? unit.getPeriod() : p.getPeriod());
      unit.setOrdering(unit.getOrdering() != null ? unit.getOrdering() : p.getOrdering());
      unit.setRounding(unit.getRounding() != null ? unit.getRounding() : p.getRounding());
      unit.setKind(unit.getKind() != null ? unit.getKind() : p.getKind());
      unit.setDefinitionUnit(unit.getDefinitionUnit() != null ? unit.getDefinitionUnit() : p.getDefinitionUnit());
      unit.setDefinitionValue(unit.getDefinitionValue() != null ? unit.getDefinitionValue() : p.getDefinitionValue());
      unit.setMappings(CollectionUtils.isNotEmpty(unit.getMappings()) ? unit.getMappings() : p.getMappings());
    });
    save(unit);
  }

  private LocalizedName mergeLocalizedName(LocalizedName names, LocalizedName persistedNames) {
    if (CollectionUtils.isEmpty(persistedNames)) {
      return names;
    }
    persistedNames.keySet().forEach(pl -> names.putIfAbsent(pl, persistedNames.get(pl)));
    return names;
  }
}
