package com.kodality.termserver.commons.util.range;

import java.math.BigDecimal;
import org.apache.commons.lang3.StringUtils;

public class BigDecimalRange extends Range<BigDecimal> {

  public BigDecimalRange() {}

  public BigDecimalRange(String range) {
    super(range);
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
