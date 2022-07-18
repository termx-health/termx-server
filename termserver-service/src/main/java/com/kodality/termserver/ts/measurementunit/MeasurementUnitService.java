package com.kodality.termserver.ts.measurementunit;

import com.kodality.termserver.measurementunit.MeasurementUnit;
import com.kodality.termserver.measurementunit.MeasurementUnitSearchParams;
import com.kodality.termserver.ts.measurementunit.mapping.MeasurementUnitMappingRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MeasurementUnitService {
  private final MeasurementUnitRepository measurementUnitRepository;
  private final MeasurementUnitMappingRepository measurementUnitMappingRepository;

  public List<MeasurementUnit> query(MeasurementUnitSearchParams params) {
    return measurementUnitRepository.query(params);
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
    List<MeasurementUnit> measurementUnits = queryByKind(kind);
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

  private List<MeasurementUnit> queryByKind(String kind) {
    MeasurementUnitSearchParams params = new MeasurementUnitSearchParams();
    params.setKind(kind);
    return query(params);
  }
}
