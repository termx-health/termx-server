package com.kodality.termserver.commons.util.range;

public class LongRange extends Range<Long> {

  public LongRange() {}

  public LongRange(String range) {
    super(range);
  }

  @Override
  protected Long parse(String input) {
    return Long.valueOf(input);
  }

  @Override
  protected String format(Long endpoint) {
    return endpoint.toString();
  }

}
