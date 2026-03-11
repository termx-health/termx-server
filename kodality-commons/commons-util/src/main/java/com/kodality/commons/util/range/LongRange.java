package com.kodality.commons.util.range;

public class LongRange extends Range<Long> {

  public LongRange(String range) {
    super(range);
  }

  public LongRange() {

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
