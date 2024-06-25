package com.kodality.termx.ucum.ts;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.ts.CodeSystemExternalProvider;
import com.kodality.termx.ucum.measurementunit.MeasurementUnitService;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ucum.MeasurementUnit;
import com.kodality.termx.ucum.MeasurementUnitQueryParams;
import jakarta.inject.Singleton;

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
  public String getCodeSystemId() {
    return UCUM;
  }
}
