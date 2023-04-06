package com.kodality.termserver.observationdefintion;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinitionCardinality {
  private BigDecimal min;
  private BigDecimal max;
}
