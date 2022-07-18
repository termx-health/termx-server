package com.kodality.termserver.measurementunit;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MeasurementUnitSearchParams {
  private String kind;
}
