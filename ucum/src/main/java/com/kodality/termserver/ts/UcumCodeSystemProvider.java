package com.kodality.termserver.ts;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.measurementunit.MeasurementUnitService;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.termserver.ucum.MeasurementUnit;
import com.kodality.termserver.ucum.MeasurementUnitQueryParams;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class UcumCodeSystemProvider extends CodeSystemExternalProvider {
  private final UcumMapper ucumMapper;
  private final MeasurementUnitService measurementUnitService;

  private static final String UCUM = "ucum";

  public UcumCodeSystemProvider(UcumMapper ucumMapper, MeasurementUnitService measurementUnitService) {
    this.ucumMapper = ucumMapper;
    this.measurementUnitService = measurementUnitService;
  }

  @Override
  public QueryResult<Concept> searchConcepts(ConceptQueryParams params) {
    MeasurementUnitQueryParams ucumParams = new MeasurementUnitQueryParams();
    ucumParams.setLimit(params.getLimit());
    ucumParams.setOffset(params.getOffset());
    ucumParams.setCode(params.getCode());
    ucumParams.setTextContains(params.getTextContains());
    ucumParams.setCodeCisEq(params.getDesignationCiEq());
    QueryResult<MeasurementUnit> result = measurementUnitService.query(ucumParams);
    QueryResult<Concept> concepts = new QueryResult<>();
    concepts.setMeta(result.getMeta());
    concepts.setData(result.getData().stream().map(ucumMapper::toConcept).toList());
    return concepts;
  }

  @Override
  public List<CodeSystemEntityVersion> loadLastVersions(List<String> code) {
    MeasurementUnitQueryParams ucumParams = new MeasurementUnitQueryParams();
    ucumParams.setCode(String.join(",", code));
    ucumParams.setLimit(code.size());
    List<MeasurementUnit> units = measurementUnitService.query(ucumParams).getData();
    return units.stream().map(ucumMapper::toConceptVersion).toList();
  }

  @Override
  public String getCodeSystemId() {
    return UCUM;
  }
}
