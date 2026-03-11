package com.kodality.commons.util.range;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

public class BigDecimalRange extends Range<BigDecimal> {

  public BigDecimalRange(String range) {
    super(range);
  }

  public BigDecimalRange() {
  }

  public static BigDecimalRange of(String range) {
    if (StringUtils.isEmpty(range)) {
      return null;
    }
    return new BigDecimalRange(range);
  }

  @Override
  protected BigDecimal parse(String input) {
    return new BigDecimal(input);
  }

  @Override
  protected String format(BigDecimal endpoint) {
    return endpoint.toString();
  }

}
