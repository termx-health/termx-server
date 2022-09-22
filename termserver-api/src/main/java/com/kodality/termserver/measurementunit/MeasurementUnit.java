package com.kodality.termserver.measurementunit;

import com.kodality.commons.model.LocalizedName;
import com.kodality.commons.util.range.LocalDateRange;
import io.micronaut.core.annotation.Introspected;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class MeasurementUnit {
  private Long id;
  private String code;
  private LocalizedName names;
  private LocalizedName alias;
  private LocalDateRange period;
  private Integer ordering;
  private BigDecimal rounding;
  private String kind;
  private String definitionUnit;
  private String definitionValue;

  private List<MeasurementUnitMapping> mappings;
}
