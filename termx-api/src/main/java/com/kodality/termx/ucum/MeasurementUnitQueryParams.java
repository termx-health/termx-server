package com.kodality.termx.ucum;

import com.kodality.commons.model.QueryParams;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MeasurementUnitQueryParams extends QueryParams {
  private String code;
  private List<String> codes;
  private String codeCisEq;
  private String kind;
  private String textContains;
  private LocalDate date;
}
