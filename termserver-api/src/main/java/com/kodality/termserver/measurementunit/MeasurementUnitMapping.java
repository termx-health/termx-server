package com.kodality.termserver.measurementunit;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MeasurementUnitMapping {
  private String system;
  private String systemUnit;
  private String systemValue;
}
