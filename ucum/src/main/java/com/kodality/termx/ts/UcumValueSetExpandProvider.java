package com.kodality.termx.ts;

import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.measurementunit.MeasurementUnitService;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ucum.MeasurementUnit;
import com.kodality.termx.ucum.MeasurementUnitQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class UcumValueSetExpandProvider extends ValueSetExternalExpandProvider {
  private final UcumMapper ucumMapper;
  private final MeasurementUnitService measurementUnitService;

  private static final String UCUM = "ucum";
  private static final String UCUM_KIND = "kind";

  public UcumValueSetExpandProvider(UcumMapper ucumMapper, MeasurementUnitService measurementUnitService) {
    this.ucumMapper = ucumMapper;
    this.measurementUnitService = measurementUnitService;
  }

  @Override
  public List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule, ValueSetVersion version) {
    List<ValueSetVersionConcept> ruleConcepts = CollectionUtils.isEmpty(rule.getConcepts()) ? new ArrayList<>() : rule.getConcepts();
    if (CollectionUtils.isEmpty(rule.getFilters())) {
      ruleConcepts.forEach(this::decorate);
      return ruleConcepts;
    }
    rule.getFilters().forEach(f -> ruleConcepts.addAll(filterConcepts(f)));
    return ruleConcepts;
  }

  private List<ValueSetVersionConcept> filterConcepts(ValueSetRuleFilter filter) {
    MeasurementUnitQueryParams params = new MeasurementUnitQueryParams();

    if (UCUM_KIND.equals(filter.getProperty().getName())) {
      params.setKind(filter.getValue());
      params.all();
    } else {
      return new ArrayList<>();
    }

    return measurementUnitService.query(params).getData().stream().map(unit -> {
      ValueSetVersionConcept concept = new ValueSetVersionConcept();
      concept.setConcept(ucumMapper.toVSConcept(unit));
      concept.setActive(true);
      concept.setAdditionalDesignations(ucumMapper.toConceptVersion(unit).getDesignations());
      return concept;
    }).collect(Collectors.toList());
  }

  private void decorate(ValueSetVersionConcept c) {
    MeasurementUnit unit = measurementUnitService.load(c.getConcept().getCode());
    Concept concept = ucumMapper.toConcept(unit);
    if (CollectionUtils.isEmpty(c.getAdditionalDesignations())) {
      c.setAdditionalDesignations(new ArrayList<>());
      c.getAdditionalDesignations().addAll(concept.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getDesignations).orElse(new ArrayList<>()));
    }
    c.setActive(true);
  }

  @Override
  public String getCodeSystemId() {
    return UCUM;
  }
}
